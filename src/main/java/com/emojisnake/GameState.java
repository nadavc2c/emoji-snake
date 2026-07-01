package com.emojisnake;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The complete Snake simulation - grid, snake, food, bonus, obstacles, score, speed, plus the
 * roguelite layer (power-ups, timed status effects, element synergies, portals) and the
 * forgiveness layer (revive, wall-wrap, merciful speed, momentum decay). Pure logic with no UI
 * dependency, so the rules are headlessly unit-tested.
 *
 * <p>Everything beyond classic Snake defaults to <em>off</em> ({@link #setMechanicsEnabled},
 * {@link #setMercyRunsEnabled}, {@link #setWrapUntilLevel}) so the deterministic seeded
 * constructor stays vanilla and the original tests are unchanged. The app opts into all of it.
 */
public final class GameState {

    /** What happened on a {@link #tick()} - drives sound effects and game-over handling. */
    public enum Event { MOVED, ATE_FOOD, ATE_BONUS, CRASHED }

    public enum Status { RUNNING, PAUSED, GAME_OVER }

    /** A richer side-channel event for juice/audio (power-ups, synergies, portals, the shed gag). */
    public record Notice(Kind kind, Element element) {
        public enum Kind { POWERUP, SYNERGY, PORTAL, SHED, RECONNECT, BOOK, FLEE, STORE, BASILISK, CURE, SLOT }
    }

    // Tuning constants.
    private static final int START_LENGTH = 3;
    private static final int MAX_QUEUED_TURNS = 2; // buffer quick tap-taps without input loss
    private static final int OBSTACLE_COUNT = 6;
    private static final int FOOD_POINTS = 1;
    private static final int BONUS_POINTS = 5;
    private static final int BONUS_EVERY_N_FOODS = 5;
    private static final int BONUS_LIFETIME_TICKS = 35;
    private static final double BASE_DELAY_MS = 170;   // calmer opening pace
    private static final double MIN_DELAY_MS = 80;     // lower speed cap (never unplayable)
    private static final double SPEEDUP_PER_FOOD_MS = 5;
    private static final double SPEED_DECAY_PER_TICK = 0.03; // pace eases back if you stop eating
    private static final double MERCY_SPEED_SCALE = 0.45;    // a "merciful run" accelerates far less
    private static final double MERCY_RUN_CHANCE = 1.0 / 3.0;
    private static final int REVIVE_GRACE_TICKS = 16;        // ghost + wall-wrap window after a revive

    // Roguelite tuning.
    private static final double POWERUP_SPAWN_CHANCE = 0.4; // per food eaten, if none on board
    private static final int POWERUP_LIFETIME_TICKS = 55;
    private static final int STATUS_TICKS = 48;
    private static final int SYNERGY_THRESHOLD = 3;
    private static final int SYNERGY_BONUS_TICKS = 80;
    private static final double SLOW_FACTOR = 1.7;
    private static final int GOLD_MULT = 2;
    private static final int PORTAL_MIN_LEVEL = 2;

    // The rare "shed body" gag: the body freezes into terrain, the head pops off and must crawl
    // back to bite its own (still-living) neck to reassemble.
    private static final int MIN_SHED_LENGTH = 6;   // only worth it once there's a real body to shed
    private static final double SHED_CHANCE = 0.0014; // per tick once eligible - deliberately very rare

    // The persistent "haircut" (BARBER) meta-upgrade: each level-up sheds `trimPerLevel` tail segments
    // (down to MIN_TRIM_LENGTH), so runaway length - the real late-game killer - stops being a death
    // sentence and the deep back-room floors become reachable. Score (food count) is untouched; only
    // the hazard shrinks. Off by default (trimPerLevel = 0), so seeded tests are byte-identical.
    private static final int MIN_TRIM_LENGTH = 5;   // never trim the snake shorter than this

    // The rare "book" pickup ("bookworm"): a food respawn occasionally becomes a 📖 instead. Eating
    // it opens a slop AI visual-novel interlude (handled app-side via the BOOK notice). Off by default.
    private static final double BOOK_CHANCE = 0.06; // fraction of food respawns that become a book

    // Fleeing food ("worm runs away"): the opposite of MAGNET. Only LIVING food (worm/mouse/chick)
    // flees - cooked food can't run. When the head gets right next to it, it bolts one cell away, a
    // random capped number of times, then gives up.
    private static final int MAX_FOOD_FLEES = 3;
    private static final double LIVING_FOOD_CHANCE = 0.14; // fraction of spawns that are living (flee)

    // On-board store: a permanent 🏪 you bump into to buy power-ups. Appears once the firm's awake.
    private static final int STORE_MIN_LEVEL = 2;

    // Gaslight gag tuning: a wall drops two cells ahead (~0.2s at high speed). The chance ramps with
    // how far past the threshold you are, so a high score gets walls in your face often, with a cooldown.
    private static final double GASLIGHT_BASE_CHANCE = 0.06;
    private static final double GASLIGHT_RAMP = 0.012; // per point over the threshold
    private static final double GASLIGHT_MAX_CHANCE = 0.6;
    private static final int GASLIGHT_COOLDOWN = 45;

    // Basilisk gag: eating roasted chicken can turn the head into a chicken 🐔 (a cockatrice), the
    // food into snakes 🐍, until an egg 🥚 is eaten. Off by default.
    private static final double BASILISK_CHANCE = 0.2; // per roasted-chicken eaten, once eligible

    // Slot-machine gag: a food respawn occasionally becomes a 🎰. Eating it doesn't grow you directly -
    // it fires a SLOT notice and the app's animated SlotInterlude spins, then calls addPendingGrowth()
    // with the won length. Off by default.
    private static final double SLOT_CHANCE = 0.07;

    private final int cols;
    private final int rows;
    private final Random rng;

    private final Deque<Point> snake = new ArrayDeque<>();
    private final Set<Point> snakeCells = new HashSet<>();
    private final Set<Point> obstacles = new HashSet<>();

    private Direction direction;
    private final Deque<Direction> turnQueue = new ArrayDeque<>();
    private Point food;
    private Tile foodTile = Tile.FOOD_APPLE;
    private boolean foodIsBook;     // the current food cell is the rare 📖 (opens the visual novel)
    private boolean booksEnabled;   // app opts in (only once the meta layer is awake)
    private boolean fleeingFoodEnabled; // food bolts when you get close (anti-magnet); app opts in
    private int foodFleesLeft;      // remaining flees for the current food (0 = it stays put)
    private boolean gambleEnabled;  // a food can be a 🎰 that adds random length; app opts in
    private boolean foodIsSlot;     // the current food is a 🎰 gamble
    private int pendingGrowth;      // length still owed (from a slot win), grown one segment per tick
    private int trimPerLevel;       // "haircut": tail segments auto-shed each level-up (0 = off); app opts in
    private Point bonus;            // nullable
    private int bonusTicksLeft;

    // On-board store (default off): a permanent shop cell; bumping it surfaces a STORE notice.
    private boolean storeEnabled;
    private Point store;            // nullable

    // Basilisk gag (default off): head -> 🐔, food -> 🐍, until the 🥚 is eaten.
    private boolean basiliskEnabled;
    private boolean basilisk;
    private Point egg;              // nullable cure, present only while basilisk

    // Gaslight gag (default off): past a per-run score threshold, rarely drop a wall right ahead of
    // the head with almost no time to react (the revive then breaks it, so it "vanishes").
    private boolean gaslightEnabled;
    private int gaslightThreshold; // per-run, rolled 40..45 when enabled
    private int gaslightCooldown;

    private int score;
    private int foodEaten;
    private Status status;

    // Forgiveness (default vanilla).
    private double speedMomentum;
    private double speedScale = 1.0;
    private boolean mercyRunsEnabled;
    private int wrapUntilLevel;
    private int wallGraceTicks;      // after a revive: walls wrap instead of kill for a few ticks

    // Roguelite (default off).
    private boolean mechanicsEnabled;
    private Point powerUp;          // nullable
    private Element powerUpElement;
    private int powerUpTicksLeft;
    private final EnumMap<StatusKind, Integer> statuses = new EnumMap<>(StatusKind.class);
    private final EnumMap<Element, Integer> elementCounts = new EnumMap<>(Element.class);
    private Point portalA;          // nullable pair
    private Point portalB;
    private final List<Notice> notices = new ArrayList<>();

    // The "shed body" gag (default off, app opts in via setGagsEnabled).
    private boolean gagsEnabled;
    private boolean detached;        // body is frozen terrain; only the head moves
    private final List<Point> frozenBody = new ArrayList<>(); // ordered neck -> tail
    private final Set<Point> frozenCells = new HashSet<>();   // O(1) lethal lookup for the frozen body
    private Point reconnectNode;     // the frozen neck; biting it reassembles the snake

    public GameState(int cols, int rows) {
        this(cols, rows, new Random());
    }

    /** Deterministic constructor for tests. */
    public GameState(int cols, int rows, long seed) {
        this(cols, rows, new Random(seed));
    }

    private GameState(int cols, int rows, Random rng) {
        this.cols = cols;
        this.rows = rows;
        this.rng = rng;
        reset();
    }

    /** Restart a fresh game on the same board. */
    public void reset() {
        snake.clear();
        snakeCells.clear();
        obstacles.clear();
        bonus = null;
        bonusTicksLeft = 0;
        score = 0;
        foodEaten = 0;
        direction = Direction.RIGHT;
        turnQueue.clear();
        status = Status.RUNNING;

        powerUp = null;
        powerUpElement = null;
        powerUpTicksLeft = 0;
        statuses.clear();
        elementCounts.clear();
        portalA = portalB = null;
        notices.clear();

        detached = false;
        frozenBody.clear();
        frozenCells.clear();
        reconnectNode = null;

        store = null;
        basilisk = false;
        egg = null;
        pendingGrowth = 0;

        // Spawn the snake horizontally in the middle, heading right.
        int cy = rows / 2;
        int headX = cols / 2;
        for (int i = 0; i < START_LENGTH; i++) {
            Point p = new Point(headX - i, cy);
            snake.addLast(p);
            snakeCells.add(p);
        }

        placeObstacles();
        food = randomFreeCell();
        assignFoodTile();

        wallGraceTicks = 0;

        // Rolled last so it never disturbs the placement RNG sequence above.
        speedMomentum = 0;
        speedScale = (mercyRunsEnabled && rng.nextDouble() < MERCY_RUN_CHANCE)
                ? MERCY_SPEED_SCALE : 1.0;
    }

    // --- configuration / hooks (app opts in; vanilla by default) ---------------

    public void setMercyRunsEnabled(boolean enabled) { this.mercyRunsEnabled = enabled; }
    public void setWrapUntilLevel(int level) { this.wrapUntilLevel = level; }
    public void setMechanicsEnabled(boolean enabled) { this.mechanicsEnabled = enabled; }
    public void setGagsEnabled(boolean enabled) { this.gagsEnabled = enabled; }
    public void setBooksEnabled(boolean enabled) { this.booksEnabled = enabled; }
    public void setFleeingFoodEnabled(boolean enabled) { this.fleeingFoodEnabled = enabled; }
    public void setStoreEnabled(boolean enabled) { this.storeEnabled = enabled; }
    public void setBasiliskEnabled(boolean enabled) { this.basiliskEnabled = enabled; }
    /** The persistent "haircut": tail segments auto-shed per level-up (0 = off, capped by the app). */
    public void setTrimPerLevel(int n) { this.trimPerLevel = Math.max(0, n); }

    /** Enable the gaslight wall and roll this run's score threshold (40..45). */
    public void setGaslightEnabled(boolean enabled) {
        this.gaslightEnabled = enabled;
        if (enabled) {
            this.gaslightThreshold = 40 + rng.nextInt(6);
        }
    }
    public void setGambleEnabled(boolean enabled) { this.gambleEnabled = enabled; }

    /** Owe the snake {@code n} extra segments, grown one per tick (the slot-machine payout). */
    public void addPendingGrowth(int n) {
        if (n > 0) {
            pendingGrowth += n;
        }
    }
    public boolean isMercifulRun() { return speedScale < 1.0; }

    /** Award bonus points (e.g. surviving a boss interlude). */
    public void addScore(int points) { score += points; }

    /** Force the run to end (e.g. lives lost during a boss interlude). */
    public void forceGameOver() { status = Status.GAME_OVER; }

    /** Grant a timed status from outside (Konami gift, boss reward). */
    public void applyStatus(StatusKind kind, int ticks) { statuses.put(kind, ticks); }

    /** Set the opening heading directly (no reversal guard) while the snake is still stationary. */
    public void setInitialDirection(Direction d) {
        if (d != null) {
            direction = d;
            turnQueue.clear();
        }
    }

    /**
     * Second chance, <em>in place</em>: keep the snake exactly where it died - same body, same
     * length, same heading - and just clear whatever killed it so it can crawl on. The obstacle it
     * ran into is broken; a brief grace (ghost + wall-wrap) lets it phase out of its own body or
     * slip through the wall it hit. Score / level / board / food are untouched, so the snake keeps
     * everything it earned. The headless tests never call it, so a crash still ends the game for
     * them.
     */
    public void revive() {
        if (status != Status.GAME_OVER) {
            return;
        }
        Point head = snake.peekFirst();
        if (head != null) {
            obstacles.remove(head.step(direction)); // break the obstacle it ran into, if any
        }
        // A short grace so it doesn't instantly re-die on the cell that just killed it: ghost
        // phases through its own body / remaining obstacles, wall-grace wraps the wall it hit.
        statuses.merge(StatusKind.GHOST, REVIVE_GRACE_TICKS, Math::max);
        wallGraceTicks = REVIVE_GRACE_TICKS;
        turnQueue.clear();
        speedMomentum = Math.max(0, speedMomentum * 0.5);
        if (food == null || snakeCells.contains(food)) {
            food = randomFreeCell();
        }
        status = Status.RUNNING;
    }

    // --- input ---------------------------------------------------------------

    /**
     * Queue a turn, validated against the last <em>queued</em> turn so two quick taps (a U-turn
     * entered before the next tick) both register. Bounded to {@value #MAX_QUEUED_TURNS}.
     */
    public void setDirection(Direction d) {
        Direction last = turnQueue.isEmpty() ? direction : turnQueue.peekLast();
        if (d == null || d == last || d == last.opposite()) {
            return;
        }
        if (turnQueue.size() < MAX_QUEUED_TURNS) {
            turnQueue.addLast(d);
        }
    }

    /** Drop any buffered turns (used by the app to ignore mashed keys right after the game stops). */
    public void clearTurnQueue() {
        turnQueue.clear();
    }

    public void togglePause() {
        if (status == Status.RUNNING) {
            status = Status.PAUSED;
        } else if (status == Status.PAUSED) {
            status = Status.RUNNING;
        }
    }

    // --- simulation ----------------------------------------------------------

    /** Advance the world by one step. Returns what notably happened. */
    public Event tick() {
        if (status != Status.RUNNING) {
            return Event.MOVED;
        }
        if (!turnQueue.isEmpty()) {
            direction = turnQueue.pollFirst();
        }

        if (detached) {
            return tickDetached();
        }

        maybeSpawnPortals();
        maybeSpawnStore();
        if (basilisk && egg == null) {
            egg = randomFreeCell(); // re-attempt the cure if the board was full when it transformed
        }
        maybeGaslight(); // rarely, drop a wall two cells ahead with almost no time to react

        Point head = snake.peekFirst();
        maybeFleeFood(head); // the worm bolts if you're right on top of it (anti-magnet)
        Point next = head.step(direction);

        // Portals teleport before anything else (they sit on in-bounds cells).
        if (mechanicsEnabled && portalA != null) {
            if (next.equals(portalA)) {
                next = portalB;
                notices.add(new Notice(Notice.Kind.PORTAL, null));
            } else if (next.equals(portalB)) {
                next = portalA;
                notices.add(new Notice(Notice.Kind.PORTAL, null));
            }
        }

        // Soft walls: wrap to the far side instead of dying, during early-game forgiveness or the
        // brief grace window right after a revive.
        boolean hitsWall = next.x() < 0 || next.y() < 0 || next.x() >= cols || next.y() >= rows;
        if (hitsWall && (level() <= wrapUntilLevel || wallGraceTicks > 0)) {
            next = new Point(Math.floorMod(next.x(), cols), Math.floorMod(next.y(), rows));
            hitsWall = false;
        }

        // The store is a solid shop: bumping it opens the buy screen (app-side) instead of moving in.
        if (store != null && next.equals(store)) {
            notices.add(new Notice(Notice.Kind.STORE, null));
            return Event.MOVED;
        }

        boolean ateFood = next.equals(food);
        boolean ateBook = ateFood && foodIsBook;            // capture before the respawn overwrites it
        boolean ateSlot = ateFood && foodIsSlot;            // 🎰 gamble for random length
        boolean ateMeat = ateFood && foodTile == Tile.FOOD_MEAT; // 🍖 can turn you basilisk
        boolean ateEgg = basilisk && egg != null && next.equals(egg); // the cure
        boolean ateBonus = bonus != null && next.equals(bonus);
        boolean atePowerUp = powerUp != null && next.equals(powerUp);
        boolean grows = ateFood && !ateSlot; // normal food grows +1; a slot is a gamble (no direct grow)
        Point tail = snake.peekLast();

        boolean hitsObstacle = obstacles.contains(next);
        if (hitsObstacle && hasStatus(StatusKind.BURN)) {
            obstacles.remove(next); // 🔥 burn straight through
            hitsObstacle = false;
        } else if (hitsObstacle && hasStatus(StatusKind.GHOST)) {
            hitsObstacle = false;   // 👻 phase through
        }
        // Colliding with the tail is safe when it is about to move out of the way.
        boolean hitsSelf = snakeCells.contains(next) && !(next.equals(tail) && !grows);
        if (hitsSelf && hasStatus(StatusKind.GHOST)) {
            hitsSelf = false;
        }

        if (hitsWall || hitsObstacle || hitsSelf) {
            status = Status.GAME_OVER;
            return Event.CRASHED;
        }

        snake.addFirst(next);
        snakeCells.add(next);

        Event event = Event.MOVED;
        boolean spawnedBonusThisTick = false;
        int mult = hasStatus(StatusKind.GOLD) ? GOLD_MULT : 1;

        if (ateFood) {
            score += FOOD_POINTS * mult;
            foodEaten++;
            food = randomFreeCell();
            assignFoodTile();
            if (ateBook) {
                // The book opens the visual novel - surfaced like any other side-event; the app
                // suspends the snake loop and takes over when it drains this.
                notices.add(new Notice(Notice.Kind.BOOK, null));
            }
            maybeBasilisk(ateMeat); // roasted chicken can hatch the cockatrice
            if (ateSlot) {
                // Pull the lever: the app's SlotInterlude spins, then calls addPendingGrowth() with the
                // win. The eat itself doesn't grow - it's a gamble.
                notices.add(new Notice(Notice.Kind.SLOT, null));
            }
            if (bonus == null && foodEaten % BONUS_EVERY_N_FOODS == 0) {
                bonus = randomFreeCell();
                bonusTicksLeft = BONUS_LIFETIME_TICKS;
                spawnedBonusThisTick = true;
            }
            if (mechanicsEnabled && powerUp == null && rng.nextDouble() < POWERUP_SPAWN_CHANCE) {
                spawnPowerUp();
            }
            event = Event.ATE_FOOD;
        } else if (ateEgg) {
            // The 🥚 cures the basilisk: head back to a snake, food back to normal.
            basilisk = false;
            egg = null;
            score += BONUS_POINTS * mult;
            notices.add(new Notice(Notice.Kind.CURE, null));
            event = Event.ATE_BONUS;
        } else if (ateBonus) {
            score += BONUS_POINTS * mult;
            bonus = null;
            event = Event.ATE_BONUS;
        } else if (atePowerUp) {
            applyPowerUp(powerUpElement);
            powerUp = null;
            powerUpElement = null;
        }

        if (!grows) {
            if (pendingGrowth > 0) {
                pendingGrowth--; // slot-machine payout: keep the tail (grow one segment) this tick
            } else {
                Point removed = snake.removeLast();
                // Only free the cell if no other segment still sits on it - while GHOST is active the
                // head can phase onto a body cell, briefly putting that Point in the deque twice; freeing
                // it on the first tail removal would desync snakeCells from the actual snake.
                if (!snake.contains(removed)) {
                    snakeCells.remove(removed);
                }
            }
        }

        // Persistent "haircut": on each level-up, auto-shed a few tail segments so length stays
        // manageable (the deep-game enabler). Runs after growth so a fresh food still counts.
        if (ateFood && trimPerLevel > 0 && foodEaten % BONUS_EVERY_N_FOODS == 0) {
            trimTail(trimPerLevel);
        }

        if (bonus != null && !spawnedBonusThisTick && --bonusTicksLeft <= 0) {
            bonus = null;
        }
        if (powerUp != null && --powerUpTicksLeft <= 0) {
            powerUp = null;
            powerUpElement = null;
        }

        tickStatuses();
        if (wallGraceTicks > 0) {
            wallGraceTicks--;
        }
        if (hasStatus(StatusKind.MAGNET) && food != null) {
            pullFoodTowardHead();
        }

        // Speed momentum: ramps up on eating, gently decays otherwise.
        if (ateFood) {
            speedMomentum += 1;
        } else {
            speedMomentum = Math.max(0, speedMomentum - SPEED_DECAY_PER_TICK);
        }

        maybeTriggerShed();
        return event;
    }

    /**
     * Current step delay in milliseconds. Shrinks with eating "momentum" (which decays if you
     * stop eating), is scaled gentler on a merciful run, and stretched while SLOW is active.
     */
    public double delayMillis() {
        double delay = BASE_DELAY_MS - speedMomentum * SPEEDUP_PER_FOOD_MS * speedScale;
        if (hasStatus(StatusKind.SLOW)) {
            delay *= SLOW_FACTOR;
        }
        return Math.max(MIN_DELAY_MS, delay);
    }

    /** Normalized current speed in {@code [0, 1]} (0 = starting, 1 = the cap). */
    public double speedFraction() {
        double range = BASE_DELAY_MS - MIN_DELAY_MS;
        if (range <= 0) return 1.0;
        double f = (BASE_DELAY_MS - delayMillis()) / range;
        return f < 0 ? 0 : (f > 1 ? 1 : f);
    }

    public int level() {
        return 1 + foodEaten / BONUS_EVERY_N_FOODS;
    }

    // --- roguelite internals -------------------------------------------------

    private void applyPowerUp(Element e) {
        statuses.merge(e.status, STATUS_TICKS, Math::max);
        int count = elementCounts.merge(e, 1, Integer::sum);
        notices.add(new Notice(Notice.Kind.POWERUP, e));
        if (count % SYNERGY_THRESHOLD == 0) {
            statuses.put(e.status, STATUS_TICKS + SYNERGY_BONUS_TICKS);
            score += BONUS_POINTS; // synergy reward
            notices.add(new Notice(Notice.Kind.SYNERGY, e));
        }
    }

    private void tickStatuses() {
        statuses.entrySet().removeIf(en -> {
            int left = en.getValue() - 1;
            en.setValue(left);
            return left <= 0;
        });
    }

    private void pullFoodTowardHead() {
        Point head = snake.peekFirst();
        int dx = Integer.signum(head.x() - food.x());
        int dy = Integer.signum(head.y() - food.y());
        // Step one axis at a time so it drifts diagonally over a couple of ticks.
        Point moved = (dx != 0) ? new Point(food.x() + dx, food.y())
                : (dy != 0) ? new Point(food.x(), food.y() + dy) : food;
        // Use the full free-cell test so the magnet never parks food on the store / a portal / bonus /
        // egg / power-up - cells where tick()'s earlier handling would make it permanently uneatable.
        if (isFreeForFood(moved)) {
            food = moved;
        }
    }

    private void spawnPowerUp() {
        Point p = randomFreeCell();
        if (p != null) {
            powerUp = p;
            powerUpElement = Element.random(rng);
            powerUpTicksLeft = POWERUP_LIFETIME_TICKS;
        }
    }

    private void maybeSpawnPortals() {
        if (mechanicsEnabled && portalA == null && level() >= PORTAL_MIN_LEVEL) {
            Point a = randomFreeCell();
            Point b = randomFreeCell();
            if (a != null && b != null && !a.equals(b)) {
                portalA = a;
                portalB = b;
            }
        }
    }

    /** Place the permanent shop once the firm is awake (it persists for the rest of the run). */
    private void maybeSpawnStore() {
        if (storeEnabled && store == null && level() >= STORE_MIN_LEVEL) {
            Point p = randomFreeCell();
            if (p != null) {
                store = p;
            }
        }
    }

    /**
     * The gaslight: once you're past this run's threshold, rarely drop a wall two cells dead ahead -
     * almost no time to react. When you hit it, {@link #revive()} breaks that very obstacle, so it
     * "vanishes" and you're left wondering if it was ever there.
     */
    private void maybeGaslight() {
        if (!gaslightEnabled || score < gaslightThreshold) {
            return;
        }
        if (gaslightCooldown > 0) {
            gaslightCooldown--;
            return;
        }
        double chance = Math.min(GASLIGHT_MAX_CHANCE,
                GASLIGHT_BASE_CHANCE + (score - gaslightThreshold) * GASLIGHT_RAMP);
        if (rng.nextDouble() >= chance) {
            return;
        }
        Point head = snake.peekFirst();
        Point spot = new Point(head.x() + direction.dx() * 2, head.y() + direction.dy() * 2);
        if (isFreeForFood(spot) && !spot.equals(food)) {
            obstacles.add(spot);
            gaslightCooldown = GASLIGHT_COOLDOWN;
        }
    }

    /**
     * Anti-magnet: if the head is right next to the food, the food bolts one cell directly away - but
     * only while it has flees left (then it gives up and lets itself be caught). Skipped under MAGNET.
     */
    private void maybeFleeFood(Point head) {
        if (!fleeingFoodEnabled || food == null || foodFleesLeft <= 0 || hasStatus(StatusKind.MAGNET)) {
            return;
        }
        int dx = food.x() - head.x();
        int dy = food.y() - head.y();
        if (Math.max(Math.abs(dx), Math.abs(dy)) != 1) {
            return; // only bolts up close - surprising, not a slow chase
        }
        Point away = new Point(food.x() + Integer.signum(dx), food.y() + Integer.signum(dy));
        if (isFreeForFood(away)) {
            food = away;
            foodFleesLeft--;
            notices.add(new Notice(Notice.Kind.FLEE, null));
            return;
        }
        // Cornered: take any free neighbour that increases distance from the head.
        for (Direction d : Direction.values()) {
            Point p = food.step(d);
            if (isFreeForFood(p) && chebyshev(p, head) > chebyshev(food, head)) {
                food = p;
                foodFleesLeft--;
                notices.add(new Notice(Notice.Kind.FLEE, null));
                return;
            }
        }
    }

    private boolean isFreeForFood(Point p) {
        if (p.x() < 0 || p.y() < 0 || p.x() >= cols || p.y() >= rows) {
            return false;
        }
        if (snakeCells.contains(p) || obstacles.contains(p) || frozenCells.contains(p)) {
            return false;
        }
        return !p.equals(bonus) && !p.equals(powerUp) && !p.equals(store) && !p.equals(egg)
                && !p.equals(portalA) && !p.equals(portalB);
    }

    private static int chebyshev(Point a, Point b) {
        return Math.max(Math.abs(a.x() - b.x()), Math.abs(a.y() - b.y()));
    }

    /** Roll the basilisk on a roasted-chicken meal: head becomes a chicken, food becomes snakes. */
    private void maybeBasilisk(boolean ateMeat) {
        if (!basiliskEnabled || basilisk || !ateMeat) {
            return;
        }
        if (rng.nextDouble() < BASILISK_CHANCE * chaosScale()) {
            basilisk = true;
            egg = randomFreeCell(); // the cure
            notices.add(new Notice(Notice.Kind.BASILISK, null));
        }
    }

    /**
     * Pick the freshly-spawned food's tile, and rarely promote it to a 📖 book (only when enabled).
     * The {@code booksEnabled} guard short-circuits before the roll, so with books off this draws
     * exactly one RNG value (the food pick) - identical to vanilla, keeping the seeded tests intact.
     */
    private void assignFoodTile() {
        foodTile = Tile.randomFood(rng);
        foodIsBook = booksEnabled && rng.nextDouble() < BOOK_CHANCE * chaosScale();
        if (foodIsBook) {
            foodTile = Tile.BOOK;
        }
        foodIsSlot = !foodIsBook && gambleEnabled && rng.nextDouble() < SLOT_CHANCE * chaosScale();
        if (foodIsSlot) {
            foodTile = Tile.SLOT;
        }
        // Only a rare LIVING food (worm/mouse/chick) flees; cooked food never runs. Guarded so a
        // vanilla game draws no extra RNG.
        boolean living = !foodIsBook && !foodIsSlot && fleeingFoodEnabled
                && rng.nextDouble() < LIVING_FOOD_CHANCE * chaosScale();
        if (living) {
            foodTile = Tile.randomLiving(rng);
            foodFleesLeft = 1 + rng.nextInt(MAX_FOOD_FLEES);
        } else {
            foodFleesLeft = 0;
        }
    }

    /**
     * Chaos multiplier that grows with progress: the deeper you are, the more often the rare gags
     * (shed / book / basilisk) fire. Draws no RNG itself; multiplies the gag chances.
     */
    private double chaosScale() {
        return Math.min(3.0, 1.0 + (level() - 1) * 0.2);
    }

    /** Shed up to {@code n} tail segments (never below {@link #MIN_TRIM_LENGTH}) - the "haircut". */
    private void trimTail(int n) {
        for (int i = 0; i < n && snake.size() > MIN_TRIM_LENGTH; i++) {
            Point removed = snake.removeLast();
            // Keep snakeCells in sync, but only free the cell if no other segment still sits on it
            // (GHOST can briefly put a Point in the deque twice) - mirrors the tail-removal in tick().
            if (!snake.contains(removed)) {
                snakeCells.remove(removed);
            }
        }
    }

    // --- the "shed body" gag -------------------------------------------------

    /** Roll the rare gag: freeze the body into terrain and pop the head off to fend for itself. */
    private void maybeTriggerShed() {
        if (!gagsEnabled || detached) {
            return;
        }
        if (snake.size() < MIN_SHED_LENGTH || hasStatus(StatusKind.GHOST)) {
            return; // need a real body, and never mid-grace (it would be unreadable)
        }
        if (rng.nextDouble() < SHED_CHANCE * chaosScale()) {
            shedBody();
        }
    }

    /**
     * Shed the body: every segment behind the head freezes in place as lethal terrain, leaving the
     * head a lone mover. The frozen neck stays "alive" - biting it reassembles the snake.
     */
    private void shedBody() {
        if (detached || snake.size() < 2) {
            return;
        }
        List<Point> all = new ArrayList<>(snake); // head .. tail
        Point head = all.get(0);
        frozenBody.clear();
        frozenCells.clear();
        for (int i = 1; i < all.size(); i++) {
            frozenBody.add(all.get(i));
            frozenCells.add(all.get(i));
        }
        reconnectNode = frozenBody.get(0); // the neck
        snake.clear();
        snakeCells.clear();
        snake.addFirst(head);
        snakeCells.add(head);
        turnQueue.clear();
        detached = true;
        notices.add(new Notice(Notice.Kind.SHED, null));
    }

    /**
     * The detached step: the popped-off head is now its OWN growing snake. It moves under player
     * control, can eat food (and grow) and power-ups, and can bite its own grown body. Frozen body
     * cells / walls / obstacles are lethal; biting the frozen neck reassembles everything - the grown
     * lone snake keeps its new length and the frozen original body is tacked onto its tail.
     */
    private Event tickDetached() {
        Point head = snake.peekFirst();
        Point next = head.step(direction);

        boolean hitsWall = next.x() < 0 || next.y() < 0 || next.x() >= cols || next.y() >= rows;
        if (hitsWall && (level() <= wrapUntilLevel || wallGraceTicks > 0)) {
            next = new Point(Math.floorMod(next.x(), cols), Math.floorMod(next.y(), rows));
            hitsWall = false;
        }

        if (next.equals(reconnectNode)) {
            reattach(next);
            notices.add(new Notice(Notice.Kind.RECONNECT, null));
            return Event.MOVED; // the RECONNECT notice drives the reward juice
        }

        boolean ateFood = next.equals(food);
        boolean ateBonus = bonus != null && next.equals(bonus);
        boolean atePowerUp = powerUp != null && next.equals(powerUp);
        boolean grows = ateFood;
        Point tail = snake.peekLast();

        boolean hitsFrozen = frozenCells.contains(next);
        boolean hitsObstacle = obstacles.contains(next);
        boolean hitsSelf = snakeCells.contains(next) && !(next.equals(tail) && !grows);
        if (hasStatus(StatusKind.GHOST)) {
            hitsFrozen = false;
            hitsObstacle = false;
            hitsSelf = false; // grace lets the head phase free
        } else if (hitsObstacle && hasStatus(StatusKind.BURN)) {
            obstacles.remove(next);
            hitsObstacle = false;
        }
        if (hitsWall || hitsFrozen || hitsObstacle || hitsSelf) {
            status = Status.GAME_OVER;
            return Event.CRASHED;
        }

        snake.addFirst(next);
        snakeCells.add(next);

        Event event = Event.MOVED;
        int mult = hasStatus(StatusKind.GOLD) ? GOLD_MULT : 1;
        if (ateFood) {
            score += FOOD_POINTS * mult;
            foodEaten++;
            food = randomFreeCell();
            foodTile = Tile.randomFood(rng);
            foodIsBook = false;
            foodIsSlot = false;   // a detached respawn is always a plain food (no stacked gag)
            foodFleesLeft = 0;
            speedMomentum += 1;
            event = Event.ATE_FOOD;
        } else if (ateBonus) {
            score += BONUS_POINTS * mult;
            bonus = null;
            event = Event.ATE_BONUS;
        } else if (atePowerUp) {
            applyPowerUp(powerUpElement);
            powerUp = null;
            powerUpElement = null;
        }

        if (!grows) {
            if (pendingGrowth > 0) {
                pendingGrowth--;
            } else {
                Point removed = snake.removeLast();
                if (!snake.contains(removed)) {
                    snakeCells.remove(removed);
                }
            }
        }

        if (bonus != null && --bonusTicksLeft <= 0) {
            bonus = null;
        }
        if (powerUp != null && --powerUpTicksLeft <= 0) {
            powerUp = null;
            powerUpElement = null;
        }
        tickStatuses();
        if (wallGraceTicks > 0) {
            wallGraceTicks--;
        }
        if (hasStatus(StatusKind.MAGNET) && food != null) {
            pullFoodTowardHead();
        }
        return event;
    }

    /**
     * Reassemble: the lone head (sitting one cell off the neck) snaps back onto the ORIGINAL straight
     * body, and the length it earned while detached is added cleanly to the TAIL (via pendingGrowth, so
     * it extends out as the snake moves) rather than leaving the wandering detached path kinked into
     * the body at a weird angle.
     */
    private void reattach(Point neck) {
        Point head = snake.peekFirst();           // the detached head, currently adjacent to the neck
        int grew = Math.max(0, snake.size() - 1); // length earned while detached (it left as length 1)
        snake.clear();
        snakeCells.clear();
        snake.addLast(head);                      // keep the head where it is...
        snakeCells.add(head);
        for (Point p : frozenBody) {              // ...and restore the original straight body behind it
            if (snakeCells.add(p)) {
                snake.addLast(p);
            }
        }
        pendingGrowth += grew;                    // earned length grows out from the tail as it moves
        // Face forward, away from the neck, so the reattached body trails behind (no instant self-bite).
        int dx = Integer.signum(head.x() - neck.x());
        int dy = Integer.signum(head.y() - neck.y());
        for (Direction d : Direction.values()) {
            if (d.dx() == dx && d.dy() == dy) {
                direction = d;
                break;
            }
        }
        statuses.merge(StatusKind.GHOST, REVIVE_GRACE_TICKS, Math::max); // brief grace at the seam
        wallGraceTicks = REVIVE_GRACE_TICKS;
        turnQueue.clear();
        frozenBody.clear();
        frozenCells.clear();
        reconnectNode = null;
        detached = false;
        score += BONUS_POINTS; // a small reward for pulling yourself back together
    }

    public boolean hasStatus(StatusKind kind) {
        return statuses.getOrDefault(kind, 0) > 0;
    }

    public int statusTicks(StatusKind kind) {
        return statuses.getOrDefault(kind, 0);
    }

    public Set<StatusKind> activeStatuses() {
        return statuses.isEmpty() ? EnumSet.noneOf(StatusKind.class) : EnumSet.copyOf(statuses.keySet());
    }

    /** Drain the side-channel events accumulated this tick (power-ups, synergies, portals). */
    public List<Notice> drainNotices() {
        if (notices.isEmpty()) {
            return List.of();
        }
        List<Notice> out = List.copyOf(notices);
        notices.clear();
        return out;
    }

    // --- rendering helpers ---------------------------------------------------

    /** The tile to draw at a cell. */
    public Tile tileAt(int x, int y) {
        Point p = new Point(x, y);
        if (p.equals(snake.peekFirst())) return basilisk ? Tile.CHICKEN : Tile.HEAD; // 🐔 the basilisk
        if (snakeCells.contains(p)) return Tile.BODY;
        if (detached && frozenCells.contains(p)) {
            // The frozen body reads as dead terrain; the still-living neck reads as "eat me".
            return p.equals(reconnectNode) ? Tile.BONUS : Tile.GRAVE;
        }
        if (store != null && p.equals(store)) return Tile.STORE;
        if (egg != null && p.equals(egg)) return Tile.EGG;
        if (obstacles.contains(p)) return Tile.obstacleAt(x, y);
        if (powerUp != null && p.equals(powerUp)) return powerUpElement.tile;
        if (p.equals(food)) return basilisk ? Tile.HEAD : foodTile; // 🐍 the basilisk hunts snakes
        if (p.equals(bonus)) return Tile.BONUS;
        if (p.equals(portalA) || p.equals(portalB)) return Tile.PORTAL;
        return Tile.EMPTY;
    }

    // --- placement -----------------------------------------------------------

    private void placeObstacles() {
        int safeRow = rows / 2;
        int placed = 0;
        int guard = 0;
        while (placed < OBSTACLE_COUNT && guard++ < 1000) {
            Point p = new Point(rng.nextInt(cols), rng.nextInt(rows));
            if (Math.abs(p.y() - safeRow) <= 1) continue;
            if (snakeCells.contains(p) || obstacles.contains(p)) continue;
            obstacles.add(p);
            placed++;
        }
    }

    /** A uniformly random cell not covered by any entity. */
    private Point randomFreeCell() {
        List<Point> free = new ArrayList<>();
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                Point p = new Point(x, y);
                if (snakeCells.contains(p) || obstacles.contains(p) || frozenCells.contains(p)) continue;
                if (p.equals(food) || p.equals(bonus) || p.equals(powerUp)) continue;
                if (p.equals(portalA) || p.equals(portalB) || p.equals(store) || p.equals(egg)) continue;
                free.add(p);
            }
        }
        if (free.isEmpty()) {
            return null; // board full
        }
        return free.get(rng.nextInt(free.size()));
    }

    // --- accessors -----------------------------------------------------------

    public int cols() { return cols; }
    public int rows() { return rows; }
    public int score() { return score; }
    /** Whether the rare 📖 can still spawn (the app retires it once every novel is read). */
    public boolean isBooksEnabled() { return booksEnabled; }
    public Status status() { return status; }
    public Point food() { return food; }
    public Point bonus() { return bonus; }
    public Point powerUp() { return powerUp; }
    public Element powerUpElement() { return powerUpElement; }
    public Point portalA() { return portalA; }
    public Point portalB() { return portalB; }
    public Direction direction() { return direction; }
    public int length() { return snake.size(); }

    /** True while the rare gag is active: body frozen as terrain, only the head moving. */
    public boolean isDetached() { return detached; }

    /** True when the current food cell is the rare 📖 book (eating it opens the visual novel). */
    public boolean isBookFood() { return foodIsBook; }

    /** True when the current food cell is a 🎰 slot gamble. */
    public boolean isSlotFood() { return foodIsSlot; }

    /** The permanent on-board store cell (null until it spawns). Bumping it opens the shop. */
    public Point store() { return store; }

    /** True while the basilisk gag is active (head is a 🐔, food shows as 🐍). */
    public boolean isBasilisk() { return basilisk; }

    /** The 🥚 cure cell while basilisk (null otherwise). */
    public Point egg() { return egg; }

    /** The frozen "neck" the head must bite to reassemble (null unless {@link #isDetached()}). */
    public Point reconnectNode() { return reconnectNode; }

    /** Defensive copy of the snake (head first) for tests/inspection. */
    public List<Point> snakeBody() { return new ArrayList<>(snake); }

    public Set<Point> obstacles() { return Set.copyOf(obstacles); }

    // --- test hooks (package-private) ----------------------------------------

    void forceFood(Point p) { this.food = p; }

    void forcePowerUp(Point p, Element e) {
        this.powerUp = p;
        this.powerUpElement = e;
        this.powerUpTicksLeft = POWERUP_LIFETIME_TICKS;
    }

    void forcePortals(Point a, Point b) {
        this.portalA = a;
        this.portalB = b;
    }

    void grantStatus(StatusKind kind, int ticks) { statuses.put(kind, ticks); }

    void addObstacle(Point p) { obstacles.add(p); }

    void clearObstacles() { obstacles.clear(); }

    /** Trigger the shed gag immediately, ignoring the rarity roll (tests + the {@code --debug} key). */
    void forceShed() { shedBody(); }

    /** Place a 📖 book on a cell (tests + the {@code --debug} key); eating it fires the BOOK notice. */
    void forceBook(Point p) {
        this.food = p;
        this.foodTile = Tile.BOOK;
        this.foodIsBook = true;
    }

    /** Place the store at a cell (tests + debug). */
    void forceStore(Point p) { this.store = p; }

    /** Test/debug hook: drop the gaslight wall two cells dead ahead of the head right now. */
    void forceGaslight() {
        Point head = snake.peekFirst();
        Point spot = new Point(head.x() + direction.dx() * 2, head.y() + direction.dy() * 2);
        if (isFreeForFood(spot) && !spot.equals(food)) {
            obstacles.add(spot);
        }
    }

    /** Set how many times the current food will flee (tests). */
    void forceFoodFlees(int n) { this.foodFleesLeft = n; }

    /** Set the current food's tile directly (tests, e.g. to feed 🍖 for the basilisk roll). */
    void forceFoodTile(Tile t) { this.foodTile = t; }

    /** Enter the basilisk state with the cure egg at {@code eggCell} (tests + the {@code --debug} key). */
    void forceBasilisk(Point eggCell) {
        this.basilisk = true;
        this.egg = eggCell;
    }

    /** Place a 🎰 gamble food on a cell (tests); eating it fires the SLOT notice (no direct growth). */
    void forceSlotFood(Point p) {
        this.food = p;
        this.foodTile = Tile.SLOT;
        this.foodIsSlot = true;
    }
}
