package com.emojisnake;

import com.emojisnake.audio.MusicScene;
import com.emojisnake.fx.Camera;
import com.emojisnake.fx.CrtOverlay;
import com.emojisnake.fx.Easings;
import com.emojisnake.fx.FxDirector;
import com.emojisnake.fx.GlitchDirector;
import com.emojisnake.fx.ParticleSystem;
import com.emojisnake.fx.RenderLayers;
import com.emojisnake.fx.TextFit;
import com.emojisnake.fx.Tween;
import com.emojisnake.mode.DodgeBossInterlude;
import com.emojisnake.mode.FakeCrashInterlude;
import com.emojisnake.mode.Interlude;
import com.emojisnake.mode.KonamiDetector;
import com.emojisnake.mode.EndingInterlude;
import com.emojisnake.mode.SecretInterlude;
import com.emojisnake.mode.SlotInterlude;
import com.emojisnake.mode.StoreInterlude;
import com.emojisnake.mode.VisualNovelInterlude;
import com.emojisnake.mode.VnArt;
import com.emojisnake.vn.VisualNovels;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

/**
 * Emoji Snake - now the eye-bleed edition. The classic grid game still lives in the pure
 * {@link GameState}; this JavaFX shell wraps it in the "crazy" layer: a layered
 * {@link RenderLayers} canvas stack, trauma {@link Camera} shake, additive {@link ParticleSystem}
 * bursts, neon motion trails, a node-level effect chain (hue cycling + bloom), a CRT overlay,
 * and a continuous adaptive chiptune via {@link SoundManager}. Everything escalates off one
 * {@link IntensityModel} spine.
 */
public class EmojiSnakeApp extends Application {

    private static final int COLS = 20;
    private static final int ROWS = 20;
    private static final int CELL = 32;
    private static final int HUD_HEIGHT = 56;
    private static final int WIDTH = COLS * CELL;
    private static final int HEIGHT = ROWS * CELL + HUD_HEIGHT;

    private static final Color BG = Color.web("#0f1410");
    private static final Color HUD_BG = Color.web("#1b241c");
    private static final Color ACCENT = Color.web("#7ee081");
    private static final Color STOCK_GREEN = Color.web("#5ff08a"); // 📈 rally: HUD readout + eat burst

    // Forgiveness + the slow "wake up" reveal.
    private static final int BASE_MAX_LIVES = 3;
    private static final int MAX_LIFE_BONUS = 2;       // persistent +max-life upgrades you can bank
    private static final int MAX_TRIM = 3;             // persistent BARBER "haircut" levels you can bank
    private static final int MAX_FLOORS = 5;           // back-room descents to the CEO ending
    private static final int META_UNLOCK_GAMES = 2;   // first 2 games stay straight; meta wakes after
    private static final int WRAP_UNTIL_LEVEL = 2;     // early walls wrap instead of kill
    private static final double FAKEOUT_CHANCE = 1.0 / 3.0; // a "death" that glitches into a revive
    private static final int BOSS_EVERY_LEVELS = 3;    // a genre-shift boss fight every N levels
    private static final double FAKECRASH_CHANCE = 0.22; // on a non-boss level-up (meta only)
    private static final double TOAST_SECONDS = 2.6;   // how long an in-play message lingers (readable)
    private static final int[] FUNNY_SCORES = {42, 67, 69, 420, 666, 1337}; // emoji-themed score gags
    private static final int SECRET_SCORE = 30;        // the Frog-Fractions ↓↓↓ secret unlocks past this
    private static final double INPUT_LOCKOUT = 0.45;  // ignore input this long after a stop (anti-mash)
    private static final double SHRINK_CHANCE = 0.03;  // per food (meta), scaled by level - window gag

    // Dark / cruel / depressive-funny copy (only seen once the meta layer is awake).
    private static final String[] FAKEOUT_MSGS = {
        "death declined.", "not yet. i'm bored.", "no. crawl more.",
        "you don't get to leave.", "denied. how tedious.", "...fine. a little longer.",
        "back to the dirt.", "i'm not done watching you."
    };
    private static final String[] LOSTLIFE_MSGS = {
        "one less.", "tick. tock.", "closer to nothing.", "that one was free. not this."
    };
    private static final String[] GAMEOVER_MSGS = {
        "IT'S OVER.", "FINALLY STILL.", "THE VOID WINS.", "NOTHING LEFT.", "YOU STOPPED."
    };
    private static final String[] QUOTES = {
        "try not to die until you're dead.", "the void is patient.",
        "joy is a bug. we never patched it.", "you again. of course.",
        "crawl. consume. repeat. despair.", "nothing you eat will fill it."
    };

    private final EmojiAtlas atlas = new EmojiAtlas();
    private final SoundManager sound = new SoundManager();
    private SaveStore saveStore = new SaveStore(); // single save file; non-final so --play redirects to temp
    private SaveStore.Save meta = new SaveStore.Save(0, 0, false); // all persistent state
    private final IntensityModel intensity = new IntensityModel();
    private final ParticleSystem particles = new ParticleSystem();
    private final Camera camera = new Camera();
    private final Tween headPop = new Tween(1.45, 1.0, 0.18, Easings::easeOutBack);
    private final Deque<double[]> headTrail = new ArrayDeque<>();
    private final java.util.Random rng = new java.util.Random();
    private final KonamiDetector konami = new KonamiDetector();
    private final VnArt vnArt = new VnArt();

    private GameState game;
    private RenderLayers layers;
    private CrtOverlay crt;
    private FxDirector fx;
    private GlitchDirector glitch;

    private int highScore;
    private boolean newBest;
    private boolean debug;
    private boolean smoke;
    private boolean forceMeta;         // --awake: force the deranged roguelite layer on from the start
    private int frameCount;

    // Forgiveness / reveal state.
    private boolean started;          // has the player begun moving this run (press-to-start)
    private boolean resumeIsRevive;   // the pending resume prompt is a death/revive (show the lives line)
    private boolean countedThisGame;  // has this run been counted toward gamesPlayed
    private int lives = BASE_MAX_LIVES;
    private int gamesPlayed;
    private boolean metaUnlocked;     // the deranged layer is awake
    private String mercyMsg = "";     // cruel mercy line, shown on the resume-after-revive prompt
    private String gameOverTitle = "GAME OVER";
    private String gameOverQuote = "press R or Enter to play again";
    private String startQuote = "again.";
    private String toast = "";        // brief in-play announcement (power-up / synergy)
    private double toastTimer;
    private Interlude interlude;      // active takeover (boss fight / fake crash), or null
    private int lastLevel = 1;        // for level-up detection
    private int lastScore;            // for funny-score gag crossing detection (67/69/420...)
    private final java.util.Set<String> vnSeenThisRun = new java.util.HashSet<>(); // no repeat VNs per run
    private String lastNovelId;       // avoid an immediate repeat across a cycle boundary
    private Stage stage;              // kept for the window-shrink gag
    private boolean shrinking;        // a window-shrink animation is in progress
    private double inputLockoutT;     // brief deafness after a stop, so mashed keys don't carry over
    private double memeFlashTimer;    // the big "6 7" meme overlay
    private int downStreak;           // consecutive ↓ presses (the Frog-Fractions secret)
    private boolean secretUsed;       // the secret has fired this run
    private boolean secretHinted;     // the one-time "press down" hint has shown
    private boolean calm;             // accessibility: dampen the eye-bleed

    private long lastNanos;
    private double accumulatorNanos;
    private int hitstopFrames;
    private double huePhase;

    private static final int TRAIL_LENGTH = 16;

    @Override
    public void start(Stage stage) throws Exception {
        List<String> raw = getParameters().getRaw();
        String snapshotArg = raw.stream().filter(s -> s.startsWith("--snapshot")).findFirst().orElse(null);
        debug = raw.contains("--debug");
        smoke = raw.contains("--smoke"); // headless self-check: run the real loop briefly, then exit
        forceMeta = raw.contains("--awake"); // skip the slow reveal - wake the deranged roguelite now

        layers = new RenderLayers(WIDTH, HEIGHT);
        crt = new CrtOverlay(WIDTH, HEIGHT);
        fx = new FxDirector(layers.content(), WIDTH, HEIGHT);
        glitch = new GlitchDirector(WIDTH, HEIGHT, HUD_HEIGHT);
        meta = saveStore.load();        // one file holds everything (high score, progress, meta)
        highScore = meta.highScore();   // mirror into the live field used by the HUD / snapshots

        if (snapshotArg != null) {
            renderSnapshotAndExit(snapshotArg);
            return;
        }

        String playArg = raw.stream().filter(s -> s.startsWith("--play")).findFirst().orElse(null);
        if (playArg != null) {
            runPlaytests(playArg); // headless scripted self-play: drive the real gags, assert, snapshot
            return;
        }

        gamesPlayed = meta.gamesPlayed(); // (meta was loaded above; the snapshot/play paths returned early)
        lives = maxLives();
        startQuote = pick(QUOTES);
        game = new GameState(COLS, ROWS);
        game.setMercyRunsEnabled(true);
        game.setWrapUntilLevel(WRAP_UNTIL_LEVEL);
        // Mechanics/gags/store are NOT turned on here: beginRun() enables them only once the meta
        // layer is awake, so the first couple of runs are pure, plain classic Snake.
        game.reset(); // re-roll the merciful-run dice now that forgiveness is configured
        if (forceMeta) {
            metaUnlocked = true;
            glitch.setMetaUnlocked(true); // --awake: skip straight to the deranged layer
        }

        Scene scene = new Scene(layers.root(), WIDTH, HEIGHT, BG);
        scene.setOnKeyPressed(e -> handleKey(e.getCode()));

        this.stage = stage; // kept for the window-shrink gag
        updateTitle(); // plain "Emoji Snake" until the deranged layer wakes, then the full title
        var iconStream = EmojiSnakeApp.class.getResourceAsStream("/icon.png");
        if (iconStream != null) {
            stage.getIcons().add(new Image(iconStream));
        }
        stage.setScene(scene);
        stage.setResizable(false);
        if (!smoke) {
            stage.show();
        }

        sound.start();
        if (smoke) {
            sound.toggle();                 // keep the self-check silent while the audio thread runs
            started = true;                 // auto-run the sim so the loop exercises movement/crash
            metaUnlocked = true;            // ...and the deranged death paths
            glitch.setMetaUnlocked(true);
            game.setInitialDirection(Direction.RIGHT);
        }
        startLoop();
    }

    @Override
    public void stop() {
        sound.close(); // release the audio thread + line cleanly
    }

    private void startLoop() {
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastNanos == 0) {
                    lastNanos = now;
                }
                double delta = now - lastNanos;
                lastNanos = now;
                double dt = Math.min(delta / 1_000_000_000.0, 0.05);
                frameCount++;
                if (toastTimer > 0) toastTimer -= dt;
                if (inputLockoutT > 0) inputLockoutT -= dt;
                if (memeFlashTimer > 0) memeFlashTimer -= dt;

                if (smoke && frameCount >= 150) {
                    System.out.println("smoke: ran " + frameCount + " frames OK");
                    Platform.exit();
                    return;
                }

                // A takeover interlude (boss fight / fake crash) fully owns the frame.
                if (interlude != null) {
                    interlude.update(dt);
                    if (interlude.isDone()) {
                        finishInterlude();
                        if (interlude != null) {
                            return; // one screen chained straight into another (store -> descend / ending)
                        }
                    } else {
                        particles.update(dt);
                        // Each takeover gets its own classical bed + a fitting energy/cleanliness:
                        // calm scenes stay clean (low corruption), the boss/ending push energy.
                        MusicScene ms = sceneFor(interlude);
                        sound.setScene(ms);
                        sound.setIntensity(switch (ms) {
                            case VN, SHOP, SECRET -> new IntensitySnapshot(0.15, 0.05, 0);
                            case BOSS -> new IntensitySnapshot(0.85, intensity.corruption(), 0);
                            case ENDING -> new IntensitySnapshot(0.6, 0.08, 0);
                            default -> new IntensitySnapshot(0.7, intensity.corruption(), 0);
                        });
                        renderInterlude();
                        return;
                    }
                }

                advanceSimulation(delta);
                checkLevelUp();

                intensity.update(game, dt);
                IntensitySnapshot snap = intensity.snapshot();
                sound.setScene(MusicScene.NORMAL); // back to the adaptive groove during play
                sound.setIntensity(snap);
                particles.update(dt);
                headPop.update(dt);

                if (!metaUnlocked) {
                    // SUPER PLAIN: the first runs are pure classic Snake. No effect chain, no shake,
                    // no glitch, no CRT bloom - render() / drawOverlay skip all of it when meta is off.
                    layers.content().setEffect(null);
                    camera.reset();
                    camera.apply(layers.content()); // identity transform -> steady board
                    render(IntensitySnapshot.CALM);
                } else {
                    IntensitySnapshot vis = calm ? snap.tamed() : snap; // calm mode tames the visuals
                    camera.update(dt, vis.intensity());
                    camera.apply(layers.content()); // shake the board; CRT overlay stays steady
                    glitch.update(vis, dt);
                    huePhase += dt * (0.25 + vis.corruption() * 1.8 + vis.intensity() * 0.6);
                    fx.update(vis, huePhase, game.direction());
                    render(vis);
                }
            }
        }.start();
    }

    private void advanceSimulation(double deltaNanos) {
        if (!started || game.status() != GameState.Status.RUNNING) {
            return; // press-to-start gate: frozen until the player makes the first move
        }
        if (hitstopFrames > 0) {
            hitstopFrames--; // freeze the sim for impact punch; visuals keep animating
            return;
        }
        accumulatorNanos += deltaNanos;
        double stepNanos = game.delayMillis() * 1_000_000.0;
        while (accumulatorNanos >= stepNanos) {
            accumulatorNanos -= stepNanos;
            step();
            // Stop once a notice (book/store/slot) opened an interlude this step, so we neither
            // keep ticking under it nor let checkLevelUp clobber it.
            if (game.status() != GameState.Status.RUNNING || hitstopFrames > 0 || interlude != null) {
                break;
            }
        }
    }

    /** On reaching a new level, maybe trigger a genre-shift boss fight or a fake crash (meta only). */
    private void checkLevelUp() {
        int level = game.level();
        if (level <= lastLevel) {
            return;
        }
        lastLevel = level;
        if (!metaUnlocked || !started || interlude != null) {
            return; // don't clobber an interlude a notice already opened this frame (slot/book/store)
        }
        if (level % BOSS_EVERY_LEVELS == 0) {
            interlude = new DodgeBossInterlude(atlas, WIDTH, HEIGHT, lives);
        } else if (rng.nextDouble() < FAKECRASH_CHANCE) {
            interlude = new FakeCrashInterlude();
        }
    }

    private void finishInterlude() {
        if (interlude instanceof DodgeBossInterlude boss) {
            lives = Math.max(0, lives - boss.hits());
            if (lives <= 0) {
                game.forceGameOver();
                realGameOver();
            } else if (boss.survived()) {
                lives = Math.min(maxLives(), lives + 1); // a mercy life for surviving
                game.addScore(25);
                game.applyStatus(StatusKind.GHOST, 60);
                resumeAfterScreen("survived. barely.", false);
            } else {
                resumeAfterScreen("the boss took a bite.", true);
            }
        } else if (interlude instanceof VisualNovelInterlude vn) {
            // Finishing a novel (any ending) marks it read - completing all 5 gates the TRUE ENDING.
            String novelId = vn.story().id();
            if (!meta.vnDone().contains(novelId)) {
                meta = meta.withVnDone(novelId);
                persist();
            }
            // A wrong choice in the novel kills you - spend a life via the same path as the boss
            // (revive() only works from GAME_OVER, so we adjust lives directly here).
            if (vn.died()) {
                lives = Math.max(0, lives - 1);
                if (lives <= 0) {
                    game.forceGameOver();
                    realGameOver();
                    gameOverTitle = "BAD END";   // a VN death is unmistakable, not a pause
                } else {
                    resumeAfterScreen("BAD END - " + lives + (lives == 1 ? " life left" : " lives left"), true);
                }
            } else {
                int reward = vn.reward();
                if (reward > 0) {
                    game.addScore(reward);
                }
                resumeAfterScreen(vn.outcomeBlurb() + (reward > 0 ? "  +" + reward : ""), false);
            }
        } else if (interlude instanceof StoreInterlude store) {
            // Apply any permanent +max-life upgrades bought this visit (persisted across runs).
            if (store.livesBought() > 0) {
                meta = meta.withMaxLifeBonus(Math.min(MAX_LIFE_BONUS, meta.maxLifeBonus() + store.livesBought()));
                persist();
                lives = Math.min(maxLives(), lives + store.livesBought());
            }
            // Apply any BARBER "haircut" levels bought this visit (persistent; takes effect immediately).
            if (store.trimBought() > 0) {
                meta = meta.withTrim(Math.min(MAX_TRIM, meta.trim() + store.trimBought()));
                persist();
                game.setTrimPerLevel(meta.trim());
            }
            if (store.boughtEnding()) {
                meta = meta.withEnded(true); // cleared every floor + bought the way out
                persist();
                interlude = new EndingInterlude(vnArt); // the "become the firm" finale
                return;
            }
            if (store.boughtSecret()) {
                int floor = Math.min(MAX_FLOORS, meta.rank() + 1); // descend one persistent floor
                meta = meta.withRank(floor);
                persist();
                Direction safe = safeStoreExit(); // pre-set a safe heading so the post-chapter prompt
                if (safe != null) {               // can't resume back into the wedged-against shop
                    game.setInitialDirection(safe);
                }
                interlude = new SecretInterlude(floor, MAX_FLOORS); // the floor's chapter
                return;
            }
            // The head is wedged against the solid 🏪, so pre-set a SAFE default heading (never into the
            // shop or the body) for a non-direction resume; the player can still pick any direction at
            // the crawl prompt. setInitialDirection sets it immediately (survives stopGrace's queue clear).
            Direction exit = store.exitDirection();
            if (exit == null || exit == game.direction() || exit == game.direction().opposite()) {
                exit = safeStoreExit();
            }
            if (exit != null) {
                game.setInitialDirection(exit);
            }
            resumeAfterScreen("thanks for shopping.", false);
        } else if (interlude instanceof EndingInterlude) {
            // The finale dismissed: end the run on a win screen (the meta progress is already saved).
            game.forceGameOver();
            realGameOver();
            gameOverTitle = "★ YOU ARE THE FIRM ★";
            gameOverQuote = "the snake ate everything. even the ending. (saved)";
        } else if (interlude instanceof SlotInterlude slot) {
            game.addPendingGrowth(slot.amount());
            if (slot.exitDirection() != null) {
                game.setInitialDirection(slot.exitDirection());
            }
            resumeAfterScreen((slot.amount() >= 5 ? "JACKPOT! +" : "+") + slot.amount() + " length", false);
        } else if (interlude instanceof SecretInterlude) {
            resumeAfterScreen("back to the surface. nothing changed.", false);
        } else {
            // Fake crash (or any other screen): also hand back to the crawl prompt, never auto-resume.
            resumeAfterScreen("...huh. false alarm.", false);
        }
        interlude = null;
        stopGrace(); // swallow keys mashed while the screen was up
    }

    /**
     * Return from any takeover screen to the "press a direction to crawl on" gate, so the snake never
     * auto-moves the instant a shop / novel / boss / secret closes. {@code revive} adds the lives line.
     */
    private void resumeAfterScreen(String prompt, boolean revive) {
        started = false;
        resumeIsRevive = revive;
        mercyMsg = prompt;
    }

    /** A safe heading to leave the store on: a free perpendicular cell (never into the shop or body). */
    private Direction safeStoreExit() {
        Point head = game.snakeBody().get(0);
        Point store = game.store();
        Direction into = game.direction();              // the head bumped the shop heading this way
        Set<Point> body = Set.copyOf(game.snakeBody());
        for (Direction d : Direction.values()) {
            if (d == into || d == into.opposite()) {
                continue; // skip into-the-shop and reverse-into-the-body
            }
            Point n = head.step(d);
            if (n.equals(store)) {
                continue;
            }
            if (n.x() < 0 || n.y() < 0 || n.x() >= game.cols() || n.y() >= game.rows()) {
                continue;
            }
            if (game.obstacles().contains(n) || body.contains(n)) {
                continue;
            }
            return d;
        }
        return null; // genuinely cornered (shop ahead, body behind, blocked both sides)
    }

    /** Current life cap: base plus any permanent VESTED LIFE upgrades banked across runs. */
    private int maxLives() {
        return BASE_MAX_LIVES + meta.maxLifeBonus();
    }

    /** Write the whole save (high score, progress, meta) to the single save file. */
    private void persist() {
        meta = meta.withHighScore(highScore).withGamesPlayed(gamesPlayed);
        saveStore.save(meta);
    }

    /** Pick a novel not yet seen this run, so a single run never repeats one until all 5 are cycled. */
    private com.emojisnake.vn.Story pickNovel() {
        if (vnSeenThisRun.size() >= VisualNovels.all().size()) {
            vnSeenThisRun.clear(); // cycled through every novel -> start a fresh cycle...
            if (lastNovelId != null) {
                vnSeenThisRun.add(lastNovelId); // ...but never immediately repeat the last one
            }
        }
        com.emojisnake.vn.Story s = VisualNovels.pickExcluding(rng, vnSeenThisRun);
        vnSeenThisRun.add(s.id());
        lastNovelId = s.id();
        return s;
    }

    /** Which classical music bed fits the active takeover screen. */
    private static MusicScene sceneFor(Interlude il) {
        if (il instanceof DodgeBossInterlude) {
            return MusicScene.BOSS;
        }
        if (il instanceof VisualNovelInterlude) {
            return MusicScene.VN;
        }
        if (il instanceof StoreInterlude) {
            return MusicScene.SHOP;
        }
        if (il instanceof EndingInterlude) {
            return MusicScene.ENDING;
        }
        if (il instanceof SecretInterlude) {
            return MusicScene.SECRET;
        }
        return MusicScene.NORMAL; // fake crash etc. - keep the groove
    }

    /** The shop, seeded with the current persistent meta (floors descended, +life upgrades, novels read). */
    private StoreInterlude newStore() {
        return new StoreInterlude(game, atlas, WIDTH, HEIGHT,
                meta.rank(), MAX_FLOORS, meta.maxLifeBonus(), MAX_LIFE_BONUS,
                meta.vnDone().size(), VisualNovels.all().size(), meta.trim(), MAX_TRIM);
    }

    private void onKonami() {
        metaUnlocked = true;
        glitch.setMetaUnlocked(true);
        applyMetaGameFlags(); // wake the gags mid-run too, not just the visuals
        lives = Math.min(maxLives(), lives + 1);
        game.applyStatus(StatusKind.GHOST, 220);
        toast("the code. of course.");
    }

    private void renderInterlude() {
        layers.clear(layers.bg());
        layers.clear(layers.trail());
        layers.clear(layers.entity());
        GraphicsContext gc = layers.overlay();
        layers.clear(gc);
        interlude.render(gc, WIDTH, HEIGHT);
        particles.render(gc);
    }

    private void step() {
        GameState.Event event = game.tick();
        intensity.registerEvent(event);
        switch (event) {
            case ATE_FOOD -> { sound.eat(); onEat(false); }
            case ATE_BONUS -> { sound.bonus(); onEat(true); }
            case CRASHED -> onCrash();
            case MOVED -> pushHeadTrail();
        }
        for (GameState.Notice n : game.drainNotices()) {
            onNotice(n);
        }
        checkFunnyScore();
        maybeSecretHint();
        // Window-shrink only on a plain food eat that did NOT open an interlude - otherwise it would
        // resize the window mid-slot/VN and clip it.
        if (event == GameState.Event.ATE_FOOD && interlude == null) {
            maybeWindowShrink();
        }
    }

    /** One-time nudge that the Frog-Fractions secret exists, once you've earned some score. */
    private void maybeSecretHint() {
        if (metaUnlocked && !secretHinted && !secretUsed && game.score() >= SECRET_SCORE) {
            secretHinted = true;
            toast("the floor feels thin here. (↓ ↓ ↓?)");
        }
    }

    /**
     * The "food that shrinks the box" gag: rarely, eating squeezes the whole window down for a few
     * seconds, then restores it. Window-only theatre; never during the headless self-checks.
     */
    private void maybeWindowShrink() {
        if (smoke || stage == null || shrinking || !metaUnlocked) {
            return;
        }
        if (rng.nextDouble() < SHRINK_CHANCE * Math.min(3, game.level())) {
            windowShrink();
        }
    }

    private void windowShrink() {
        shrinking = true;
        toast("the walls are closing in.");
        final double small = 0.62;
        // Window chrome (title bar + borders): outer size minus the WIDTH×HEIGHT client area.
        final double chromeW = stage.getWidth() - WIDTH;
        final double chromeH = stage.getHeight() - HEIGHT;
        // Scale the board from the TOP-LEFT (not the centre), so the shrunk board stays pinned to the
        // window corner and the whole field is visible - the old centre-pivot scale cropped the edges
        // off-screen, which got the player killed against a wall they couldn't see.
        var scale = new javafx.scene.transform.Scale(1, 1, 0, 0);
        layers.root().getTransforms().add(scale);
        var s = new javafx.beans.property.SimpleDoubleProperty(1.0);
        s.addListener((o, ov, nv) -> {
            double f = nv.doubleValue();
            scale.setX(f);
            scale.setY(f);
            stage.setWidth(WIDTH * f + chromeW);   // client = WIDTH*f, so the scaled board fits exactly
            stage.setHeight(HEIGHT * f + chromeH);
        });
        var tl = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.ZERO,
                        new javafx.animation.KeyValue(s, 1.0)),
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(0.3),
                        new javafx.animation.KeyValue(s, small)),
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(2.8),
                        new javafx.animation.KeyValue(s, small)),
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(3.1),
                        new javafx.animation.KeyValue(s, 1.0)));
        tl.setOnFinished(e -> {
            layers.root().getTransforms().remove(scale);
            stage.setWidth(WIDTH + chromeW);
            stage.setHeight(HEIGHT + chromeH);
            stage.centerOnScreen();
            shrinking = false;
        });
        tl.play();
    }

    /** Easter-egg gag when the score crosses a comedic number (67 / 69 / 420 / ...). */
    private void checkFunnyScore() {
        int score = game.score();
        if (score != lastScore) {
            for (int target : FUNNY_SCORES) {
                if (lastScore < target && score >= target) {
                    funnyScoreGag(target);
                }
            }
            lastScore = score;
        }
    }

    private void funnyScoreGag(int target) {
        String msg = switch (target) {
            case 42 -> "42. the answer to everything.";
            case 67 -> "6-7. ...sure.";
            case 69 -> "69. nice.";
            case 420 -> "420. blaze it.";
            case 666 -> "666. ominous.";
            case 1337 -> "1337. l33t.";
            default -> target + "!";
        };
        Color color = switch (target) {
            case 420 -> Color.web("#5ee06a");
            case 69 -> Color.web("#ff5a9e");
            case 666 -> Color.web("#ff2d55");
            case 1337 -> Color.web("#7df9ff");
            default -> Color.web("#ffd54a");
        };
        double[] c = cellCenter(game.snakeBody().get(0));
        particles.burst(c[0], c[1], (int) (52 * juice()), color, 300);
        camera.addTrauma(0.4 * juice());
        camera.kick(0.06 * juice());
        headPop.restart();
        sound.bonus();
        if (target == 67) {
            // THE meme deserves the full treatment: a screaming red "6 7" with horns, not a toast.
            memeFlashTimer = 2.2;
            camera.addTrauma(0.7 * juice());
            particles.burst(WIDTH / 2.0, HEIGHT / 2.0, (int) (80 * juice()), Color.web("#ff2d2d"), 360);
        } else {
            toast(msg);
        }
    }

    /** Juice for the roguelite side-events: power-ups, synergies, portals. */
    private void onNotice(GameState.Notice n) {
        double[] c = cellCenter(game.snakeBody().get(0));
        switch (n.kind()) {
            case POWERUP -> {
                sound.bonus();
                particles.burst(c[0], c[1], 26, elementColor(n.element()), 220);
                camera.addTrauma(0.22);
                camera.kick(0.04);
                toast(statusName(n.element().status) + "!");
            }
            case SYNERGY -> {
                sound.bonus();
                particles.burst(c[0], c[1], 60, elementColor(n.element()), 320);
                camera.addTrauma(0.5);
                camera.kick(0.08);
                toast(n.element().name() + " SYNERGY");
            }
            case PORTAL -> {
                sound.turn();
                particles.burst(c[0], c[1], 30, Color.web("#b07dff"), 260);
                camera.kick(0.05);
            }
            case SHED -> {
                // The body just fell apart. Make it feel like a wrong, jolting event.
                sound.crash();
                camera.addTrauma(0.6 * juice());
                camera.kick(0.08 * juice());
                Point neck = game.reconnectNode();
                if (neck != null) {
                    double[] nc = cellCenter(neck);
                    particles.burst(nc[0], nc[1], (int) (40 * juice()), Color.web("#7ee081"), 260);
                }
                toast(metaUnlocked ? "you came apart. bite your neck." : "you fell apart! eat the 🗿");
            }
            case RECONNECT -> {
                sound.bonus();
                particles.burst(c[0], c[1], (int) (50 * juice()), Color.web("#7df9ff"), 300);
                camera.addTrauma(0.3 * juice());
                camera.kick(0.07 * juice());
                headPop.restart();
                toast(metaUnlocked ? "reassembled. ugh." : "back in one piece!");
            }
            case BOOK -> {
                // Bookworm. Suspend the snake and open a (rare) slop AI visual novel. Assigned here
                // inside step() so it's picked up at the top of the next frame, like checkLevelUp.
                sound.bonus();
                particles.burst(c[0], c[1], (int) (34 * juice()), Color.web("#d7b16a"), 240);
                camera.kick(0.05 * juice());
                interlude = new VisualNovelInterlude(pickNovel(), vnArt, metaUnlocked);
            }
            case FLEE -> {
                // The worm bolted. A quick whoosh where it landed.
                sound.turn();
                Point f = game.food();
                if (f != null) {
                    double[] fc = cellCenter(f);
                    particles.burst(fc[0], fc[1], (int) (16 * juice()), Color.web("#9be08a"), 200);
                }
            }
            case STORE -> {
                // Bumped the shop. Open the buy screen (picked up next frame, like the VN). Guard so a
                // repeated bump in the same frame's step loop doesn't recreate it.
                if (interlude == null) {
                    sound.bonus();
                    interlude = newStore();
                }
            }
            case BASILISK -> {
                sound.crash();
                particles.burst(c[0], c[1], (int) (40 * juice()), Color.web("#ffd54a"), 280);
                camera.addTrauma(0.4 * juice());
                camera.kick(0.07 * juice());
                headPop.restart();
                toast(metaUnlocked ? "you are become chicken." : "BAWK! eat the 🥚 to change back!");
            }
            case CURE -> {
                sound.bonus();
                particles.burst(c[0], c[1], (int) (30 * juice()), Color.web("#fff3b0"), 240);
                camera.kick(0.05 * juice());
                toast("snake again. mostly.");
            }
            case SLOT -> {
                // Pull the lever: suspend the snake and spin the reels (picked up next frame).
                if (interlude == null) {
                    sound.bonus();
                    interlude = new SlotInterlude(atlas);
                }
            }
            case STOCK -> {
                // Bought a share: the portfolio (score multiplier) just grew. Green "up" juice.
                sound.bonus();
                particles.burst(c[0], c[1], (int) (28 * juice()), STOCK_GREEN, 240);
                camera.kick(0.05 * juice());
                toast(metaUnlocked
                        ? String.format(Locale.ROOT, "📈 +1 share  ·  portfolio %.1fx", game.stockMultiplier())
                        : String.format(Locale.ROOT, "📈 stock! score x%.1f", game.stockMultiplier()));
            }
            case CRASH -> {
                // Margin call: the market corrected and halved the portfolio. Red "down" jolt.
                sound.crash();
                particles.burst(c[0], c[1], (int) (34 * juice()), Color.web("#ff4d6d"), 260);
                camera.addTrauma(0.35 * juice());
                camera.kick(0.06 * juice());
                toast(metaUnlocked
                        ? String.format(Locale.ROOT, "📉 MARGIN CALL  ·  down to %.1fx", game.stockMultiplier())
                        : "📉 the market crashed!");
            }
        }
    }

    private static Color elementColor(Element e) {
        return switch (e) {
            case FIRE -> Color.web("#ff6a2b");
            case ICE -> Color.web("#7df9ff");
            case GHOST -> Color.web("#e0d7ff");
            case GOLD -> Color.web("#ffd54a");
            case MAGNET -> Color.web("#ff5a9e");
        };
    }

    private static String statusName(StatusKind s) {
        return switch (s) {
            case BURN -> "BURN";
            case SLOW -> "SLOW";
            case GHOST -> "GHOST";
            case GOLD -> "GOLD x2";
            case MAGNET -> "MAGNET";
        };
    }

    private void toast(String msg) {
        toast = msg;
        toastTimer = TOAST_SECONDS;
    }

    private double juice() {
        return calm ? 0.35 : 1.0;
    }

    private void onEat(boolean bonus) {
        pushHeadTrail();
        double[] c = cellCenter(game.snakeBody().get(0));
        Color color = bonus ? Color.web("#7df9ff") : Color.web("#ff4d4d");
        particles.burst(c[0], c[1], (int) ((bonus ? 36 : 18) * juice()), color, bonus ? 260 : 180);
        camera.addTrauma((bonus ? 0.38 : 0.2) * juice());
        camera.kick((bonus ? 0.06 : 0.03) * juice());
        headPop.restart();
        hitstopFrames = bonus ? 3 : 2;
    }

    /**
     * Crash handling = the forgiveness + derangement core. Always plays the impact juice, then
     * decides the snake's fate: a meta-gated "fake death" that glitches into a free revive, a life
     * spent on a respawn, or - out of lives - the real end. The deranged commentary only appears
     * once the meta layer is awake (after the first couple of games).
     */
    private void onCrash() {
        sound.crash();
        double[] c = cellCenter(game.snakeBody().get(0));
        particles.burst(c[0], c[1], (int) (64 * juice()), Color.web("#ff2d55"), 320);
        camera.addTrauma(0.85 * juice());
        camera.kick(0.09 * juice());
        hitstopFrames = 6;

        boolean fakeout = metaUnlocked && rng.nextDouble() < FAKEOUT_CHANCE;
        if (fakeout) {
            game.revive();
            started = false; // press to keep crawling
            showMercy(pick(FAKEOUT_MSGS));
        } else if (lives > 1) {
            lives--;
            game.revive();
            started = false;
            showMercy(metaUnlocked ? pick(LOSTLIFE_MSGS) : "lives left: " + lives);
        } else {
            lives = 0;
            realGameOver();
        }
    }

    private void realGameOver() {
        gameOverTitle = metaUnlocked ? pick(GAMEOVER_MSGS) : "GAME OVER";
        gameOverQuote = pick(QUOTES);
        if (game.score() > highScore) {
            highScore = game.score();
            newBest = true;
        }
        persist(); // one file: high score + games-played + meta all written together
    }

    private void showMercy(String msg) {
        mercyMsg = msg;
        resumeIsRevive = true; // a mercy prompt always follows a death/revive, so show the lives line
        stopGrace();
    }

    /** After a stop (revive / interlude exit), deafen input briefly and drop buffered turns. */
    private void stopGrace() {
        inputLockoutT = INPUT_LOCKOUT;
        game.clearTurnQueue();
    }

    private String pick(String[] options) {
        return options[rng.nextInt(options.length)];
    }

    private void pushHeadTrail() {
        double[] c = cellCenter(game.snakeBody().get(0));
        headTrail.addFirst(c);
        while (headTrail.size() > TRAIL_LENGTH) {
            headTrail.removeLast();
        }
    }

    private double[] cellCenter(Point p) {
        return new double[] {p.x() * CELL + CELL / 2.0, HUD_HEIGHT + p.y() * CELL + CELL / 2.0};
    }

    private void handleKey(KeyCode code) {
        if (interlude != null) {
            if (code == KeyCode.ESCAPE) {
                Platform.exit();
            } else {
                interlude.handleKey(code);
            }
            return;
        }
        // Just stopped (lost a life / left the shop / finished an interlude): swallow mashed keys for a
        // beat so a panicking player doesn't instantly continue in a stale direction.
        if (inputLockoutT > 0 && code != KeyCode.ESCAPE) {
            return;
        }
        if (konami.feed(code)) {
            onKonami();
        }

        // Global utility keys work in every state (and never start a waiting run by themselves).
        switch (code) {
            case M -> { sound.toggle(); return; }
            case C -> { calm = !calm; toast(calm ? "calm mode" : "eye-bleed mode"); return; }
            case ESCAPE -> { Platform.exit(); return; }
            case OPEN_BRACKET -> { if (debug) intensity.nudgeCorruption(-0.1); return; }
            case CLOSE_BRACKET -> { if (debug) intensity.nudgeCorruption(0.1); return; }
            case U -> { if (debug) { metaUnlocked = !metaUnlocked; glitch.setMetaUnlocked(metaUnlocked); applyMetaGameFlags(); } return; }
            case G -> { if (debug && started && game.status() == GameState.Status.RUNNING) game.forceShed(); return; }
            case B -> {
                if (debug && started && game.status() == GameState.Status.RUNNING) {
                    interlude = new VisualNovelInterlude(pickNovel(), vnArt, metaUnlocked);
                }
                return;
            }
            case N -> { // debug: open the store
                if (debug && started && game.status() == GameState.Status.RUNNING) {
                    interlude = newStore();
                }
                return;
            }
            case J -> { // debug: hatch the basilisk (egg drops dead ahead)
                if (debug && started && game.status() == GameState.Status.RUNNING) {
                    Point h = game.snakeBody().get(0);
                    game.forceBasilisk(h.step(game.direction()));
                }
                return;
            }
            case K -> { // debug: drop a 📈 stock dead ahead (eat it to grow the portfolio)
                if (debug && started && game.status() == GameState.Status.RUNNING) {
                    Point h = game.snakeBody().get(0);
                    game.forceStockFood(h.step(game.direction()));
                }
                return;
            }
            default -> { /* fall through to gameplay keys */ }
        }

        Direction dir = switch (code) {
            case UP, W -> Direction.UP;
            case DOWN, S -> Direction.DOWN;
            case LEFT, A -> Direction.LEFT;
            case RIGHT, D -> Direction.RIGHT;
            default -> null;
        };

        // Waiting at the press-to-start / press-to-continue prompt: (almost) any key gets you
        // moving - a direction key picks the heading, anything else keeps the current one. This is
        // why Space no longer "pauses" the frozen pre-start board.
        if (!started && game.status() == GameState.Status.RUNNING) {
            beginRun(dir != null ? dir : game.direction());
            return;
        }

        if (dir != null) {
            // The Frog-Fractions secret: ↓ ↓ ↓ in a row (once awake + past the hint score) drops you
            // into "the real game". Any other direction resets the streak.
            if (started && game.status() == GameState.Status.RUNNING) {
                if (dir == Direction.DOWN) {
                    if (++downStreak >= 3 && metaUnlocked && !secretUsed && game.score() >= SECRET_SCORE) {
                        downStreak = 0;
                        secretUsed = true;
                        interlude = new SecretInterlude();
                        return;
                    }
                } else {
                    downStreak = 0;
                }
            }
            steer(dir);
            return;
        }
        switch (code) {
            // Pause only toggles during active play; resume only from a pause. It never fires on the
            // game-over / press-to-start screens (so those can't be mistaken for a pause).
            case SPACE, P -> {
                if (started && game.status() == GameState.Status.RUNNING) {
                    game.togglePause();
                } else if (game.status() == GameState.Status.PAUSED) {
                    game.togglePause();
                }
            }
            case R, ENTER -> restart();
            default -> { /* ignore */ }
        }
    }

    /** Begin (or resume after a revive) a waiting run, heading {@code d}; counts the game once. */
    private void beginRun(Direction d) {
        started = true;
        game.setInitialDirection(d); // open in any direction, no reversal guard
        if (!countedThisGame) {
            gamesPlayed++;
            persist();
            countedThisGame = true;
            metaUnlocked = forceMeta || gamesPlayed > META_UNLOCK_GAMES;
            glitch.setMetaUnlocked(metaUnlocked);
            applyMetaGameFlags(); // configure the gags ONCE per run (not on every revive/resume)
        }
    }

    /**
     * Turn the gameplay gags on/off to match {@code metaUnlocked}: EVERYTHING beyond plain Snake is
     * gated here, so the first couple of runs are SUPER plain and the wake-up lands hard. Called once
     * per run from {@link #beginRun}, and on the mid-run wake paths (Konami / debug {@code U}).
     */
    private void applyMetaGameFlags() {
        game.setMechanicsEnabled(metaUnlocked);   // power-ups, synergies, portals
        game.setGagsEnabled(metaUnlocked);        // the rare "shed body" gag
        game.setStoreEnabled(metaUnlocked);       // the on-board shop only exists once awake
        // Books deliver the 5 required novels; once every novel is read they'd only interrupt the long
        // score-farming runs the deep floors need, so retire them at 5/5.
        game.setBooksEnabled(metaUnlocked && meta.vnDone().size() < VisualNovels.all().size());
        game.setFleeingFoodEnabled(metaUnlocked); // food bolts when you crowd it
        game.setBasiliskEnabled(metaUnlocked);    // roasted chicken can hatch the cockatrice
        game.setGambleEnabled(metaUnlocked);      // a food can be a 🎰 slot machine
        game.setStocksEnabled(metaUnlocked);      // 📈 stocks compound score (the deep-floor rally)
        game.setTrimPerLevel(metaUnlocked ? meta.trim() : 0); // the persistent BARBER haircut
        // The gaslight is ONLY for the plain pre-wake games: it caps a too-long plain run (walls in
        // your face burn your lives) to push you to the wake-up. Once awake, the chaos does that job.
        game.setGaslightEnabled(!metaUnlocked);
        updateTitle(); // the title hint ("eye-bleed edition") only appears once it's awake
    }

    /** Window title: plain for the pre-wake runs (no "eye-bleed" hint), full once the meta is awake. */
    private void updateTitle() {
        if (stage != null) {
            stage.setTitle(metaUnlocked ? "🐍 Emoji Snake - eye-bleed edition" : "🐍 Emoji Snake");
        }
    }

    /** Steer an already-moving snake (turns are buffered in {@link GameState}'s queue). */
    private void steer(Direction d) {
        game.setDirection(d);
    }

    private void restart() {
        if (game.status() == GameState.Status.GAME_OVER) {
            game.reset(); // re-rolls the merciful-run dice
            intensity.reset();
            camera.reset();
            particles.clear();
            headTrail.clear();
            lives = maxLives();
            started = false;          // a fresh run waits for press-to-start...
            countedThisGame = false;  // ...and counts toward gamesPlayed on the first move
            newBest = false;
            mercyMsg = "";
            resumeIsRevive = false;
            vnSeenThisRun.clear();    // a fresh run may see any novel again
            lastNovelId = null;
            startQuote = pick(QUOTES);
            interlude = null;
            lastLevel = 1;
            lastScore = 0;
            downStreak = 0;
            secretUsed = false;
            secretHinted = false;
            memeFlashTimer = 0;
            inputLockoutT = 0;
            accumulatorNanos = 0;
            lastNanos = 0;
            hitstopFrames = 0;
        }
    }

    // --- rendering -----------------------------------------------------------

    private void render(IntensitySnapshot snap) {
        drawBackground();
        if (metaUnlocked) {
            drawTrail(snap); // the neon motion trail is part of the eye-bleed; off in plain runs
        } else {
            layers.clear(layers.trail());
        }
        drawEntities(snap);
        drawOverlay(snap);
    }

    private void drawBackground() {
        GraphicsContext gc = layers.bg();
        gc.setGlobalBlendMode(BlendMode.SRC_OVER);
        gc.setFill(BG);
        gc.fillRect(0, 0, WIDTH, HEIGHT);
        // Faint grass texture under the board so entities stand out.
        gc.setGlobalAlpha(0.22);
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                if (game.tileAt(x, y) == Tile.EMPTY) {
                    atlas.draw(gc, Tile.EMPTY, x * CELL, HUD_HEIGHT + y * CELL, CELL);
                }
            }
        }
        gc.setGlobalAlpha(1.0);
    }

    private void drawTrail(IntensitySnapshot snap) {
        GraphicsContext gc = layers.trail();
        layers.clear(gc);
        if (headTrail.isEmpty()) return;

        gc.setGlobalBlendMode(BlendMode.ADD);
        double hueDeg = (Math.toDegrees(huePhase) * 1.7) % 360;
        int i = 0;
        int n = headTrail.size();
        for (double[] c : headTrail) {
            double f = 1.0 - (double) i / n;            // newest = brightest
            double size = CELL * (0.65 + 0.5 * f);
            gc.setGlobalAlpha(f * (0.25 + 0.45 * snap.heat()));
            gc.setFill(Color.hsb((hueDeg + i * 12) % 360, 0.9, 1.0));
            gc.fillOval(c[0] - size / 2, c[1] - size / 2, size, size);
            i++;
        }
        gc.setGlobalAlpha(1.0);
        gc.setGlobalBlendMode(BlendMode.SRC_OVER);
    }

    private void drawEntities(IntensitySnapshot snap) {
        GraphicsContext gc = layers.entity();
        layers.clear(gc);
        drawHud(gc);
        drawStatusIcons(gc);
        drawPortalGlow(gc, game.portalA()); // under the tiles, so the spiral reads as a real portal
        drawPortalGlow(gc, game.portalB());
        drawBoard(gc);
        if (game.isDetached()) {
            drawReconnectHint(gc);
        }
        if (metaUnlocked) {
            particles.render(gc); // no particle bursts in the plain pre-wake runs
        }
    }

    /** A pulsing neon halo + rings behind a portal so the 🌀 actually pops off the board. */
    private void drawPortalGlow(GraphicsContext gc, Point p) {
        if (p == null) {
            return;
        }
        double[] c = cellCenter(p);
        double pulse = 0.5 + 0.5 * Math.sin(huePhase * 5.0);
        gc.setGlobalBlendMode(BlendMode.ADD);
        // Keep the halo inside the cell (radius <= ~0.5*CELL) so it doesn't bleed into neighbours.
        gc.setGlobalAlpha(0.30 + 0.30 * pulse);
        gc.setFill(Color.web("#b07dff"));
        double r1 = CELL * (0.40 + 0.10 * pulse);
        gc.fillOval(c[0] - r1, c[1] - r1, r1 * 2, r1 * 2);
        gc.setGlobalAlpha(0.55 + 0.4 * pulse);
        gc.setStroke(Color.web("#e2c6ff"));
        gc.setLineWidth(2);
        double r2 = CELL * (0.42 + 0.06 * pulse);
        gc.strokeOval(c[0] - r2, c[1] - r2, r2 * 2, r2 * 2);
        gc.setGlobalAlpha(1.0);
        gc.setGlobalBlendMode(BlendMode.SRC_OVER);
    }

    /** A pulsing ring around the still-living neck during the shed gag - "bite here to reassemble". */
    private void drawReconnectHint(GraphicsContext gc) {
        Point neck = game.reconnectNode();
        if (neck == null) {
            return;
        }
        double[] c = cellCenter(neck);
        double pulse = 0.5 + 0.5 * Math.sin(huePhase * 6.0);
        double r = CELL * (0.55 + 0.25 * pulse);
        gc.setGlobalBlendMode(BlendMode.ADD);
        gc.setGlobalAlpha(0.35 + 0.45 * pulse);
        gc.setStroke(ACCENT);
        gc.setLineWidth(3);
        gc.strokeOval(c[0] - r, c[1] - r, r * 2, r * 2);
        gc.setGlobalAlpha(1.0);
        gc.setGlobalBlendMode(BlendMode.SRC_OVER);
    }

    /** The "6 7" meme: a screaming red number flanked by horned 👹, with a scrim + jitter. */
    private void drawMemeFlash(GraphicsContext gc) {
        double cx = WIDTH / 2.0;
        double cy = HUD_HEIGHT + (HEIGHT - HUD_HEIGHT) / 2.0;
        double pulse = 0.5 + 0.5 * Math.sin(huePhase * 16.0);
        gc.setFill(Color.color(0, 0, 0, 0.5));
        gc.fillRect(0, HUD_HEIGHT, WIDTH, HEIGHT - HUD_HEIGHT);
        double s = 90 + 16 * pulse;
        double bob = Math.sin(huePhase * 8.0) * 8;
        atlas.draw(gc, Tile.BOSS, cx - 215, cy - s / 2 + bob, s); // 👹 = the horns
        atlas.draw(gc, Tile.BOSS, cx + 215 - s, cy - s / 2 - bob, s);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        double jx = (pulse - 0.5) * 10;
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 132));
        gc.setFill(Color.web("#7a0000"));
        gc.fillText("6 7", cx + jx + 4, cy + 4);
        gc.setFill(Color.web("#ff2d2d"));
        gc.fillText("6 7", cx + jx, cy);
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 22));
        gc.setFill(Color.web("#ffd54a"));
        gc.fillText("six. seven.", cx, cy + 94);
    }

    /** A small row of active-status emoji just below the HUD. */
    private void drawStatusIcons(GraphicsContext gc) {
        Set<StatusKind> active = game.activeStatuses();
        if (active.isEmpty()) return;
        double x = 10;
        double y = HUD_HEIGHT + 6;
        for (StatusKind s : active) {
            atlas.draw(gc, statusTile(s), x, y, 22);
            x += 26;
        }
    }

    private static Tile statusTile(StatusKind s) {
        return switch (s) {
            case BURN -> Tile.FIRE;
            case SLOW -> Tile.ICE;
            case GHOST -> Tile.GHOST;
            case GOLD -> Tile.GOLD;
            case MAGNET -> Tile.MAGNET;
        };
    }

    private void drawHud(GraphicsContext gc) {
        gc.setFill(HUD_BG);
        gc.fillRect(0, 0, WIDTH, HUD_HEIGHT);

        double mid = HUD_HEIGHT / 2.0;
        double j = glitch.jitter(); // HUD text starts trembling at high corruption
        gc.setTextBaseline(VPos.CENTER);
        atlas.draw(gc, Tile.HEAD, 12, mid - 16, 32);

        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(Color.WHITE);
        String scoreStr = "Score " + game.score();
        gc.fillText(glitch.garble(scoreStr), 52 + j, mid + j);

        // Lives as red hearts, just after the score.
        if (lives > 0) {
            gc.setFill(Color.web("#ff5a6a"));
            double heartsX = 52 + scoreStr.length() * 12.0 + 14;
            gc.fillText("♥".repeat(lives), heartsX + j, mid + j);
        }

        gc.setFill(ACCENT);
        gc.setTextAlign(TextAlignment.CENTER);
        String best = "Best " + highScore
                + (meta.rank() > 0 ? "   Floor " + meta.rank() + "/" + MAX_FLOORS : "")
                + (meta.trim() > 0 ? "   ✂L" + meta.trim() : "")
                + (meta.ended() ? " ★" : "");
        gc.fillText(glitch.garble(best), WIDTH / 2.0 - j, mid - j);

        gc.setFill(Color.web("#cfd6cf"));
        gc.setTextAlign(TextAlignment.RIGHT);
        String flags = (sound.isEnabled() ? "" : "  (muted)") + (calm ? "  (calm)" : "");
        String lvlStr = "Lvl " + game.level() + flags;
        gc.fillText(glitch.garble(lvlStr), WIDTH - 14 + j, mid + j);

        // Live 📈 portfolio multiplier (only while holding shares), tucked to the LEFT of the level in
        // the far-right cluster - so it never overlaps the centered "Best…" text, even at huge scores.
        if (game.shares() > 0) {
            gc.setFill(STOCK_GREEN);
            gc.fillText(String.format(Locale.ROOT, "▲%.1fx", game.stockMultiplier()),
                    WIDTH - 14 - lvlStr.length() * 11.0 - 12 + j, mid + j);
        }
    }

    private void drawBoard(GraphicsContext gc) {
        boolean ghost = game.hasStatus(StatusKind.GHOST); // phased snake renders see-through
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                Tile tile = game.tileAt(x, y);
                if (tile == Tile.EMPTY) {
                    continue; // grass lives on the background layer
                }
                double px = x * CELL;
                double py = HUD_HEIGHT + y * CELL;
                boolean isSnake = tile == Tile.HEAD || tile == Tile.BODY;
                if (ghost && isSnake) {
                    gc.setGlobalAlpha(0.5);
                }
                if (tile == Tile.HEAD) {
                    double scale = headPop.value();
                    double size = CELL * scale;
                    double off = (CELL - size) / 2.0;
                    atlas.draw(gc, tile, px + off, py + off, size);
                } else {
                    atlas.draw(gc, tile, px, py, CELL);
                }
                gc.setGlobalAlpha(1.0);
            }
        }
    }

    private void drawOverlay(IntensitySnapshot snap) {
        GraphicsContext gc = layers.overlay();
        layers.clear(gc);

        // Full-screen color wash - a subtle flowing accent (kept gentle; the hue rotation in the
        // effect chain does the heavy psychedelic lifting, so this never washes the board out).
        double corruption = snap.corruption();
        if (corruption > 0.02 || snap.intensity() > 0.3) {
            double hueDeg = (Math.toDegrees(huePhase) * 2.3) % 360;
            gc.setGlobalBlendMode(BlendMode.SOFT_LIGHT);
            gc.setGlobalAlpha(0.04 + corruption * 0.14);
            gc.setFill(Color.hsb(hueDeg, 0.8, 0.8));
            gc.fillRect(0, HUD_HEIGHT, WIDTH, HEIGHT - HUD_HEIGHT);
            gc.setGlobalAlpha(1.0);
            gc.setGlobalBlendMode(BlendMode.SRC_OVER);
        }

        // Gated chromatic aberration (snapshots content - fires only at high corruption), then
        // the glitch bursts / hostile text, then the CRT on top. ALL skipped in the plain pre-wake
        // runs so they're indistinguishable from vanilla Snake (no CRT, no bloom-wash, no glitch).
        if (metaUnlocked) {
            fx.drawAberration(gc, layers.content(), snap, frameCount);
            glitch.render(gc);
            crt.draw(gc, 0.12 + corruption * 0.5);
        }

        if (toastTimer > 0 && game.status() == GameState.Status.RUNNING && started) {
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setTextBaseline(VPos.CENTER);
            gc.setFont(TextFit.fit(toast, "Consolas", FontWeight.BOLD, 26, WIDTH - 40));
            gc.setFill(ACCENT);
            gc.fillText(toast, WIDTH / 2.0, HUD_HEIGHT + 40);
        }

        if (memeFlashTimer > 0 && game.status() == GameState.Status.RUNNING && started) {
            drawMemeFlash(gc);
        }

        switch (game.status()) {
            case PAUSED -> drawText(gc, "PAUSED", List.of("press Space or P to resume"));
            case GAME_OVER -> drawText(gc, gameOverTitle, List.of(
                    "score " + game.score() + (newBest ? "   - your new peak. congrats?" : ""),
                    metaUnlocked ? gameOverQuote : "press R or Enter to play again"));
            case RUNNING -> {
                if (!started) {
                    drawStartPrompt(gc); // press-to-start / resume-after-revive
                }
            }
        }
    }

    /** The press-to-start (or press-to-continue-after-a-revive) overlay. */
    private void drawStartPrompt(GraphicsContext gc) {
        String title;
        List<String> lines;
        if (countedThisGame) {
            // Mid-run: returning from a revive OR any screen (shop / novel / boss / secret). Lead with
            // the context line, then always nudge them to steer out - the snake never auto-crawls.
            title = mercyMsg.isEmpty() ? "..." : mercyMsg;
            lines = resumeIsRevive
                    ? List.of(lives > 0 ? lives + " left" : "last chance", "press a direction to crawl on")
                    : List.of("press a direction to crawl on");
        } else {
            title = metaUnlocked ? startQuote : "EMOJI SNAKE";
            lines = List.of("press  ↑ ↓ ← →  or  WASD  to begin");
        }
        drawText(gc, title, lines);
    }

    private void drawText(GraphicsContext gc, String title, List<String> lines) {
        gc.setFill(Color.color(0, 0, 0, 0.6));
        gc.fillRect(0, HUD_HEIGHT, WIDTH, HEIGHT - HUD_HEIGHT);

        double cx = WIDTH / 2.0;
        double cy = HUD_HEIGHT + (HEIGHT - HUD_HEIGHT) / 2.0;
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);

        gc.setFill(ACCENT);
        gc.setFont(TextFit.fit(title, "Segoe UI", FontWeight.BOLD, 44, WIDTH - 40));
        gc.fillText(title, cx, cy - 28);

        gc.setFill(Color.WHITE);
        double y = cy + 18;
        for (String line : lines) {
            gc.setFont(TextFit.fit(line, "Segoe UI", FontWeight.NORMAL, 18, WIDTH - 40));
            gc.fillText(line, cx, y);
            y += 26;
        }
    }

    // --- snapshot self-check -------------------------------------------------

    /**
     * Headless verification: build a deterministic board, render one frame, snapshot the render
     * root (so the node effect chain is included), write a PNG, then exit. Variants via the arg:
     * {@code :corrupt} forces the awakened high-corruption look, {@code :start} shows the
     * press-to-start screen instead of gameplay.
     */
    private void renderSnapshotAndExit(String arg) {
        if (arg.contains("boss") || arg.contains("crash")) {
            renderInterludeSnapshotAndExit(arg.contains("boss"));
            return;
        }
        if (arg.contains("vn")) {
            renderVnSnapshotAndExit();
            return;
        }
        if (arg.contains("store")) {
            renderStoreSnapshotAndExit();
            return;
        }
        if (arg.contains("slot")) {
            renderSlotSnapshotAndExit();
            return;
        }
        boolean corrupt = arg.contains("corrupt");
        boolean startScreen = arg.contains("start");
        boolean shed = arg.contains("shed");
        game = new GameState(COLS, ROWS, 42L);
        started = !startScreen;          // start screen shows the prompt; otherwise render gameplay
        countedThisGame = !startScreen;
        if (!startScreen) {
            for (int i = 0; i < 5; i++) {
                Point head = game.snakeBody().get(0);
                game.forceFood(head.step(game.direction()));
                GameState.Event ev = game.tick();
                intensity.registerEvent(ev);
                pushHeadTrail();
            }
            if (shed) {
                game.forceShed(); // freeze the body into terrain; the head pops off alone
            } else {
                // Showcase the roguelite tiles + a status for the snapshot.
                Point head = game.snakeBody().get(0);
                game.forcePowerUp(new Point(Math.min(head.x() + 2, COLS - 1), head.y()), Element.FIRE);
                game.forcePortals(new Point(3, 4), new Point(16, 15));
                game.grantStatus(StatusKind.GHOST, 300);
            }
        }
        if (corrupt) {
            intensity.nudgeCorruption(0.9);
            metaUnlocked = true;
            glitch.setMetaUnlocked(true); // show the awakened meta layer in the corrupt shot
        }
        // Drive a few model updates so intensity/corruption settle to a representative frame.
        for (int i = 0; i < 30; i++) {
            intensity.update(game, 1.0 / 60.0);
            huePhase += 0.08;
        }
        IntensitySnapshot snap = intensity.snapshot();
        glitch.update(snap, 3.0); // large dt drains the cooldown so a glitch burst shows in the shot
        fx.update(snap, huePhase, game.direction());

        // Lay out the root (a Scene triggers the layout pass) before snapshotting.
        Scene scene = new Scene(layers.root(), WIDTH, HEIGHT, BG);
        render(snap);
        particles.update(0.016);

        try {
            SnapshotParameters params = new SnapshotParameters();
            WritableImage image = layers.root().snapshot(params, new WritableImage(WIDTH, HEIGHT));
            String name = startScreen ? "snapshot-start.png"
                    : shed ? "snapshot-shed.png"
                    : corrupt ? "snapshot-corrupt.png" : "snapshot.png";
            File out = new File("build/" + name);
            Files.createDirectories(out.getParentFile().toPath());
            javax.imageio.ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", out);
            System.out.println("Wrote " + out.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Snapshot failed: " + e.getMessage());
        }
        Platform.exit();
    }

    private void renderInterludeSnapshotAndExit(boolean boss) {
        Interlude il = boss ? new DodgeBossInterlude(atlas, WIDTH, HEIGHT, 3) : new FakeCrashInterlude();
        for (int i = 0; i < 120; i++) {
            il.update(1.0 / 60.0); // populate projectiles / advance the trace
        }
        Scene scene = new Scene(layers.root(), WIDTH, HEIGHT, BG);
        layers.clear(layers.bg());
        layers.clear(layers.trail());
        layers.clear(layers.entity());
        layers.clear(layers.overlay());
        il.render(layers.overlay(), WIDTH, HEIGHT);
        try {
            SnapshotParameters params = new SnapshotParameters();
            WritableImage image = layers.root().snapshot(params, new WritableImage(WIDTH, HEIGHT));
            File out = new File("build/" + (boss ? "snapshot-boss.png" : "snapshot-crash.png"));
            Files.createDirectories(out.getParentFile().toPath());
            javax.imageio.ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", out);
            System.out.println("Wrote " + out.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Snapshot failed: " + e.getMessage());
        }
        Platform.exit();
    }

    private void renderVnSnapshotAndExit() {
        // Fixed novel; advance enough to fully reveal the opening line so the choices show.
        VisualNovelInterlude vn = new VisualNovelInterlude(VisualNovels.all().get(0), vnArt, true);
        for (int i = 0; i < 260; i++) {
            vn.update(1.0 / 60.0);
        }
        Scene scene = new Scene(layers.root(), WIDTH, HEIGHT, BG);
        layers.clear(layers.bg());
        layers.clear(layers.trail());
        layers.clear(layers.entity());
        layers.clear(layers.overlay());
        vn.render(layers.overlay(), WIDTH, HEIGHT);
        try {
            SnapshotParameters params = new SnapshotParameters();
            WritableImage image = layers.root().snapshot(params, new WritableImage(WIDTH, HEIGHT));
            File out = new File("build/snapshot-vn.png");
            Files.createDirectories(out.getParentFile().toPath());
            javax.imageio.ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", out);
            System.out.println("Wrote " + out.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Snapshot failed: " + e.getMessage());
        }
        Platform.exit();
    }

    private void renderStoreSnapshotAndExit() {
        game = new GameState(COLS, ROWS, 42L);
        game.addScore(160); // give the shopper something to spend (shows several rows affordable)
        StoreInterlude store = new StoreInterlude(game, atlas, WIDTH, HEIGHT, 2, MAX_FLOORS, 1, MAX_LIFE_BONUS, 3, 5, 1, MAX_TRIM);
        for (int i = 0; i < 30; i++) {
            store.update(1.0 / 60.0);
        }
        Scene scene = new Scene(layers.root(), WIDTH, HEIGHT, BG);
        layers.clear(layers.bg());
        layers.clear(layers.trail());
        layers.clear(layers.entity());
        layers.clear(layers.overlay());
        store.render(layers.overlay(), WIDTH, HEIGHT);
        try {
            SnapshotParameters params = new SnapshotParameters();
            WritableImage image = layers.root().snapshot(params, new WritableImage(WIDTH, HEIGHT));
            File out = new File("build/snapshot-store.png");
            Files.createDirectories(out.getParentFile().toPath());
            javax.imageio.ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", out);
            System.out.println("Wrote " + out.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Snapshot failed: " + e.getMessage());
        }
        Platform.exit();
    }

    private void renderSlotSnapshotAndExit() {
        SlotInterlude slot = new SlotInterlude(atlas);
        for (int i = 0; i < 190; i++) { // advance past the reveal (~3.1s)
            slot.update(1.0 / 60.0);
        }
        Scene scene = new Scene(layers.root(), WIDTH, HEIGHT, BG);
        layers.clear(layers.bg());
        layers.clear(layers.trail());
        layers.clear(layers.entity());
        layers.clear(layers.overlay());
        slot.render(layers.overlay(), WIDTH, HEIGHT);
        try {
            SnapshotParameters params = new SnapshotParameters();
            WritableImage image = layers.root().snapshot(params, new WritableImage(WIDTH, HEIGHT));
            File out = new File("build/snapshot-slot.png");
            Files.createDirectories(out.getParentFile().toPath());
            javax.imageio.ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", out);
            System.out.println("Wrote " + out.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Snapshot failed: " + e.getMessage());
        }
        Platform.exit();
    }

    // --- self-play test harness (--play) ------------------------------------
    //
    // Drives the REAL game (step/onNotice/finishInterlude/interludes) fast + headless through scripted
    // scenarios, asserting state transitions and dropping build/play-*.png at each beat. Reuses the
    // snapshot mechanism; no window is shown and the window-shrink gag self-disables (stage == null).

    private final java.util.List<String> playReport = new java.util.ArrayList<>();
    private java.util.List<String> scenarioFails = new java.util.ArrayList<>();
    private String scenarioName = "";
    private boolean playFailed;

    private void runPlaytests(String arg) {
        int i = arg.indexOf(':');
        String only = (i >= 0 && i + 1 < arg.length()) ? arg.substring(i + 1) : "all";
        new Scene(layers.root(), WIDTH, HEIGHT, BG); // a Scene drives the layout pass needed to snapshot
        sound.start();
        sound.toggle(); // keep the self-play silent
        metaUnlocked = true;
        glitch.setMetaUnlocked(true);
        // Never pollute the player's real save from the self-test: redirect the store to a throwaway
        // dir. Try the system temp dir, but always fall back to build/playtest so a persisting scenario
        // can never clobber the real save.dat.
        java.nio.file.Path sink = java.nio.file.Path.of("build", "playtest");
        try {
            java.nio.file.Path tmp = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "emoji-snake-playtest");
            java.nio.file.Files.createDirectories(tmp);
            sink = tmp;
        } catch (Exception ignored) {
            try {
                java.nio.file.Files.createDirectories(sink);
            } catch (Exception ignored2) {
                // fall through with the relative path; worst case the saves fail silently (still not real)
            }
        }
        saveStore = new SaveStore(sink.resolve("save.dat"));

        runOne(only, "food", this::playFood);
        runOne(only, "powerup", this::playPowerup);
        runOne(only, "shed", this::playShed);
        runOne(only, "slot", this::playSlot);
        runOne(only, "stock", this::playStock);
        runOne(only, "book-open", this::playBookOpen);
        runOne(only, "book-win", this::playBookWin);
        runOne(only, "book-death", this::playBookDeath);
        runOne(only, "store-buy", this::playStoreBuy);
        runOne(only, "store-exit", this::playStoreExit);
        runOne(only, "barber", this::playBarber);
        runOne(only, "book-retire", this::playBookRetire);
        runOne(only, "basilisk", this::playBasilisk);
        runOne(only, "fleeing", this::playFleeing);
        runOne(only, "funny67", this::playFunny67);
        runOne(only, "gaslight", this::playGaslight);
        runOne(only, "secret", this::playSecret);
        runOne(only, "descend", this::playDescend);
        runOne(only, "vn-norepeat", this::playVnNoRepeat);
        runOne(only, "ending-locked", this::playEndingBlockedUntilNovelsRead);
        runOne(only, "ending", this::playEnding);
        runOne(only, "win", this::playWin);

        System.out.println("\n=== PLAY REPORT ===");
        playReport.forEach(System.out::println);
        long passed = playReport.stream().filter(s -> s.startsWith("PASS")).count();
        System.out.println("RESULT: " + passed + "/" + playReport.size() + " scenarios passed"
                + (playFailed ? "  <<< FAIL" : "  - all good"));
        sound.close();
        System.exit(playFailed ? 1 : 0); // non-zero on any failure so `gradlew run` / CI catches it
    }

    private void runOne(String only, String name, Runnable body) {
        if (!only.equals("all") && !only.equals(name)) {
            return;
        }
        scenarioName = name;
        scenarioFails = new java.util.ArrayList<>();
        try {
            body.run();
        } catch (Throwable t) {
            scenarioFails.add("EXCEPTION " + t);
        }
        if (scenarioFails.isEmpty()) {
            playReport.add("PASS  " + name);
        } else {
            playReport.add("FAIL  " + name + " -> " + String.join("; ", scenarioFails));
            playFailed = true;
        }
    }

    private void check(boolean ok, String desc) {
        if (!ok) {
            scenarioFails.add(desc);
        }
    }

    /** Fresh seeded, meta-awake, mid-run game with the structural gags on (book/slot/etc. via force). */
    private GameState beginPlay(long seed) {
        game = new GameState(COLS, ROWS, seed);
        game.setMechanicsEnabled(true);
        game.setStoreEnabled(true);
        game.setInitialDirection(Direction.RIGHT);
        interlude = null;
        started = true;
        resumeIsRevive = false;
        countedThisGame = true;
        lives = maxLives();
        hitstopFrames = 0;
        memeFlashTimer = 0;
        inputLockoutT = 0;
        lastScore = 0;
        lastLevel = 1;
        downStreak = 0;
        secretUsed = false;
        secretHinted = false;
        vnSeenThisRun.clear();
        lastNovelId = null;
        accumulatorNanos = 0;
        return game;
    }

    private Point ahead() {
        return game.snakeBody().get(0).step(game.direction());
    }

    private void playRenderAndSnap(String beat) {
        if (interlude != null) {
            renderInterlude();
        } else {
            IntensitySnapshot snap = intensity.snapshot();
            fx.update(snap, huePhase, game.direction());
            render(snap);
        }
        try {
            WritableImage image = layers.root().snapshot(new SnapshotParameters(), new WritableImage(WIDTH, HEIGHT));
            File out = new File("build/play-" + scenarioName + "-" + beat + ".png");
            Files.createDirectories(out.getParentFile().toPath());
            javax.imageio.ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", out);
        } catch (Exception e) {
            System.err.println("play snapshot failed: " + e.getMessage());
        }
    }

    private void playFood() {
        beginPlay(5L);
        playRenderAndSnap("setup");
        int len = game.length();
        int score = game.score();
        game.forceFood(ahead());
        step();
        check(game.length() == len + 1, "did not grow (len " + game.length() + ")");
        check(game.score() == score + 1, "did not score (score " + game.score() + ")");
        check(interlude == null, "a plain food opened an interlude");
        playRenderAndSnap("after");
    }

    private void playPowerup() {
        beginPlay(5L);
        game.forcePowerUp(ahead(), Element.GHOST);
        step();
        check(game.activeStatuses().contains(StatusKind.GHOST), "eating the power-up did not grant GHOST");
        check(interlude == null, "a power-up opened an interlude");
        playRenderAndSnap("after");
    }

    private void playShed() {
        beginPlay(5L);
        game.forceFood(new Point(0, 0)); // park it so the lone head doesn't grow unexpectedly first
        game.forceShed();
        check(game.isDetached(), "forceShed did not detach the head");
        playRenderAndSnap("detached");
        int len = game.length();
        game.forceFood(ahead());
        step();
        check(game.isDetached(), "eating ended the shed gag early");
        check(game.length() == len + 1, "the lone head did not grow into its own snake");
        playRenderAndSnap("ate-detached");
    }

    private void playSlot() {
        beginPlay(5L);
        game.forceSlotFood(ahead());
        step();
        check(interlude instanceof SlotInterlude, "eating the slot did not open the reels");
        playRenderAndSnap("reels");
        if (interlude instanceof SlotInterlude slot) {
            for (int n = 0; n < 240 && !interlude.isDone(); n++) {
                interlude.update(1.0 / 60);
            }
            check(!interlude.isDone(), "the slot auto-resumed instead of waiting for the player");
            playRenderAndSnap("reveal");
            int amount = slot.amount();
            check(amount >= 1 && amount <= 6, "payout out of range: " + amount);
            interlude.handleKey(KeyCode.SPACE);
            check(interlude.isDone(), "a keypress after the reveal did not dismiss it");
            finishInterlude();
            check(interlude == null, "did not resume after the slot");
        }
        playRenderAndSnap("after");
    }

    private void playBookOpen() {
        beginPlay(5L);
        game.forceBook(ahead());
        step();
        check(interlude instanceof VisualNovelInterlude, "eating the book did not open a visual novel");
        playRenderAndSnap("vn");
        // Walk it to some ending generically and resume, to leave clean state.
        for (int n = 0; n < 12 && interlude != null && !interlude.isDone(); n++) {
            interlude.handleKey(KeyCode.SPACE);
            interlude.handleKey(KeyCode.DIGIT1);
        }
        if (interlude != null && interlude.isDone()) {
            finishInterlude();
        }
    }

    private void playBookWin() {
        beginPlay(5L);
        interlude = new VisualNovelInterlude(
                VisualNovels.all().stream().filter(s -> s.id().equals("billable")).findFirst().orElseThrow(),
                vnArt, true);
        playRenderAndSnap("opening");
        vnReveal();
        interlude.handleKey(KeyCode.DIGIT2); // bill ruthlessly -> rival junction
        vnReveal();
        interlude.handleKey(KeyCode.DIGIT1); // bury the rival -> ruthless WIN ending
        VisualNovelInterlude vn = (VisualNovelInterlude) interlude;
        check(!vn.died(), "the ruthless win was wrongly lethal");
        check(vn.reward() >= 20, "a route win paid only " + vn.reward());
        int score = game.score();
        int reward = vn.reward();
        vnReveal(); // reveal the ending line for the screenshot
        playRenderAndSnap("win");
        interlude.handleKey(KeyCode.SPACE); // acknowledge the ending
        finishInterlude();
        check(game.score() == score + reward, "the win reward was not paid (score " + game.score() + ")");
    }

    private void playBookDeath() {
        beginPlay(5L);
        interlude = new VisualNovelInterlude(
                VisualNovels.all().stream().filter(s -> s.id().equals("billable")).findFirst().orElseThrow(),
                vnArt, true);
        vnReveal();
        interlude.handleKey(KeyCode.DIGIT1); // refuse to bill a dying man -> super-kind -> LETHAL
        VisualNovelInterlude vn = (VisualNovelInterlude) interlude;
        check(vn.died(), "the super-kind choice was not lethal");
        int before = lives;
        vnReveal(); // reveal the bad-end line for the screenshot
        playRenderAndSnap("badend");
        interlude.handleKey(KeyCode.SPACE); // acknowledge
        finishInterlude();
        check(lives == before - 1, "a VN death did not cost a life (lives " + lives + ")");
    }

    /** First keypress at a node just fast-forwards the typewriter; this consumes that. */
    private void vnReveal() {
        interlude.handleKey(KeyCode.SPACE);
    }

    private void playStoreBuy() {
        beginPlay(5L);
        game.addScore(50);
        game.forceStore(ahead());
        step();
        check(interlude instanceof StoreInterlude, "bumping the store did not open the shop");
        playRenderAndSnap("shop");
        int score = game.score();
        interlude.handleKey(KeyCode.DIGIT3); // GHOST - 15 pts
        check(game.score() == score - 15, "the purchase did not deduct score");
        check(game.activeStatuses().contains(StatusKind.GHOST), "the bought status was not granted");
        interlude.handleKey(KeyCode.UP); // leave heading UP
        check(interlude.isDone(), "a direction did not close the shop");
        finishInterlude();
        check(interlude == null, "did not resume after shopping");
        step(); // the exit heading is a queued turn - it applies on the next tick
        check(game.direction() == Direction.UP, "did not leave on the chosen heading (" + game.direction() + ")");
        playRenderAndSnap("after");
    }

    /** Eat {@code n} food in a straight clear line with the given haircut level; return final length. */
    private int eatStraight(int n, int trim) {
        beginPlay(7L);
        game.clearObstacles();      // guarantee a clear runway so the streak never crashes
        game.setTrimPerLevel(trim);
        for (int i = 0; i < n && interlude == null && game.status() == GameState.Status.RUNNING; i++) {
            game.forceFood(ahead());
            step();
        }
        return game.length();
    }

    private void playBarber() {
        int classic = eatStraight(5, 0);       // no barber: START_LENGTH + 5
        int trimmed = eatStraight(5, 2);       // barber L2: sheds 2 on the level-up
        check(trimmed < classic, "the BARBER did not shorten the snake (" + trimmed + " vs " + classic + ")");
        int floored = eatStraight(5, 9);       // an aggressive haircut still respects the floor length
        check(floored >= 5, "the BARBER trimmed below the minimum length (" + floored + ")");
        playRenderAndSnap("barbered");
    }

    private void playBookRetire() {
        beginPlay(5L);
        metaUnlocked = true;
        meta = new SaveStore.Save(0, 0, false);           // no novels read yet
        applyMetaGameFlags();
        check(game.isBooksEnabled(), "books should still drop while novels remain unread");
        java.util.Set<String> allVn = new java.util.HashSet<>();
        for (com.emojisnake.vn.Story s : VisualNovels.all()) {
            allVn.add(s.id());
        }
        meta = new SaveStore.Save(0, 0, false, allVn);    // every novel read -> retire books
        applyMetaGameFlags();
        check(!game.isBooksEnabled(), "books must retire once all novels are read");
    }

    private void playStoreExit() {
        beginPlay(5L);
        game.forceStore(ahead()); // store dead ahead; head is wedged against it heading RIGHT
        step();
        check(interlude instanceof StoreInterlude, "store did not open");
        interlude.handleKey(KeyCode.ENTER); // exit "into" the shop - must be substituted, not honored
        finishInterlude();
        check(interlude == null, "shop did not close");
        step(); // applies the substituted safe turn AND should move off the shop without re-bumping
        check(game.direction() != Direction.RIGHT && game.direction() != Direction.LEFT,
                "store exit pointed into the shop / reversed (soft-lock): " + game.direction());
        check(interlude == null, "the shop re-opened immediately (soft-lock)");
        playRenderAndSnap("after");
    }

    private void playBasilisk() {
        beginPlay(5L);
        game.forceBasilisk(ahead()); // egg drops dead ahead, head becomes a chicken
        check(game.isBasilisk(), "forceBasilisk did not transform the head");
        playRenderAndSnap("chicken");
        step(); // head moves onto the egg -> cured
        check(!game.isBasilisk(), "eating the egg did not cure the basilisk");
        playRenderAndSnap("cured");
    }

    private void playFleeing() {
        beginPlay(5L);
        game.setFleeingFoodEnabled(true);
        game.forceFood(ahead());   // adjacent, dead ahead
        game.forceFoodFlees(2);
        Point before = game.food();
        step();
        check(!game.food().equals(before), "the living food failed to flee");
        check(game.length() == 3, "the worm was eaten instead of bolting (len " + game.length() + ")");
        playRenderAndSnap("bolted");
        // And a non-fleeing (0 flees) food is simply eaten.
        game.forceFood(ahead());
        game.forceFoodFlees(0);
        int len = game.length();
        step();
        check(game.length() == len + 1, "a food with no flees left was not eaten");
    }

    private void playFunny67() {
        beginPlay(5L);
        game.addScore(66);
        game.forceFood(ahead());
        step(); // -> score 67
        check(game.score() == 67, "score did not reach 67 (was " + game.score() + ")");
        check(memeFlashTimer > 0, "the 6-7 meme flash did not fire");
        playRenderAndSnap("meme");
    }

    private void playGaslight() {
        beginPlay(5L);
        game.forceFood(new Point(0, 0)); // park food clear of the head's path
        Point head = game.snakeBody().get(0);
        Point wall = new Point(head.x() + game.direction().dx() * 2, head.y() + game.direction().dy() * 2);
        game.forceGaslight(); // a wall drops two cells dead ahead
        check(game.obstacles().contains(wall), "the gaslight wall did not appear two cells ahead");
        playRenderAndSnap("wall");
        step(); // head advances one cell (wall still one ahead)
        step(); // head hits the wall -> crash -> revive in place, which BREAKS the wall (it "vanishes")
        check(game.status() == GameState.Status.RUNNING, "the gaslight crash should revive, not end the run");
        check(!started, "after the crash it waits at the press-to-continue gate");
        check(!game.obstacles().contains(wall), "revive should break the wall so it vanishes (the gaslight)");
    }

    private void playSecret() {
        beginPlay(5L);
        game.addScore(SECRET_SCORE); // the ↓↓↓ secret needs score >= SECRET_SCORE + meta
        handleKey(KeyCode.DOWN);
        handleKey(KeyCode.DOWN);
        handleKey(KeyCode.DOWN);
        check(interlude instanceof SecretInterlude, "press ↓↓↓ did not open the secret");
        // The secret is paged now: snapshot each beat, pressing a key to advance (guard against runaway).
        for (int n = 0; n < 10 && interlude != null && !interlude.isDone(); n++) {
            playRenderAndSnap("beat" + n);
            interlude.handleKey(KeyCode.SPACE);
        }
        if (interlude != null && interlude.isDone()) {
            finishInterlude();
        }
        check(interlude == null, "did not resume after the secret");
    }

    private void playDescend() {
        beginPlay(5L);
        meta = new SaveStore.Save(0, 0, false); // start at floor 0
        game.addScore(50);
        game.forceStore(ahead());
        step(); // store opens via the STORE notice
        check(interlude instanceof StoreInterlude, "store did not open");
        interlude.handleKey(KeyCode.DIGIT8); // THE BACK ROOM - descend a floor (7 is now the BARBER)
        finishInterlude();
        check(meta.rank() == 1, "descending did not advance the persistent floor (rank " + meta.rank() + ")");
        check(interlude instanceof SecretInterlude, "the floor chapter did not open");
        playRenderAndSnap("floor1");
        for (int n = 0; n < 6 && interlude != null && !interlude.isDone(); n++) {
            interlude.handleKey(KeyCode.SPACE);
        }
        if (interlude != null && interlude.isDone()) {
            finishInterlude();
        }
        check(interlude == null, "did not resume after the floor chapter");
    }

    private void playVnNoRepeat() {
        beginPlay(5L);
        int total = VisualNovels.all().size();
        java.util.Set<String> picked = new java.util.HashSet<>();
        for (int n = 0; n < total; n++) {
            picked.add(pickNovel().id()); // one full cycle must be all-distinct
        }
        check(picked.size() == total, "a run repeated a novel within one cycle (" + picked.size() + "/" + total + ")");
    }

    private void playEndingBlockedUntilNovelsRead() {
        beginPlay(5L);
        meta = new SaveStore.Save(MAX_FLOORS, 0, false); // all floors, but NO novels read yet
        game.addScore(400);
        game.forceStore(ahead());
        step();
        check(interlude instanceof StoreInterlude, "store did not open");
        interlude.handleKey(KeyCode.DIGIT9); // try to buy the ending
        check(!((StoreInterlude) interlude).boughtEnding(), "the ending must stay locked until all 5 novels are read");
        check(game.score() == 400, "the locked ending must not charge");
    }

    private void playEnding() {
        beginPlay(5L);
        // every floor cleared AND every novel read -> the ending is unlocked
        java.util.Set<String> allVn = new java.util.HashSet<>();
        for (com.emojisnake.vn.Story s : VisualNovels.all()) {
            allVn.add(s.id());
        }
        meta = new SaveStore.Save(MAX_FLOORS, 0, false, allVn);
        game.addScore(400);
        game.forceStore(ahead());
        step();
        check(interlude instanceof StoreInterlude, "store did not open");
        interlude.handleKey(KeyCode.DIGIT9); // ★ BECOME THE FIRM ★ (the real ending)
        finishInterlude();
        check(meta.ended(), "buying the true ending did not mark the game beaten");
        check(interlude instanceof EndingInterlude, "the CEO finale did not open");
        playRenderAndSnap("finale");
        for (int n = 0; n < 8 && interlude != null && !interlude.isDone(); n++) {
            interlude.handleKey(KeyCode.SPACE);
        }
        if (interlude != null && interlude.isDone()) {
            finishInterlude();
        }
        check(game.status() == GameState.Status.GAME_OVER, "the finale should end the run on a win screen");
    }

    private void playStock() {
        beginPlay(5L);
        game.setStocksEnabled(true);
        game.clearObstacles();
        playRenderAndSnap("setup");
        game.forceStockFood(ahead());
        // Drive the tick directly (not step()) so we can inspect notices before they're dispatched: the
        // STOCK notice is queued when the share is bought, BEFORE the end-of-tick crash roll - so this
        // check is immune to the (rare) same-tick margin call that could otherwise halve shares to 0.
        GameState.Event ev = game.tick();
        check(ev == GameState.Event.ATE_FOOD, "the 📈 was not eaten as food");
        java.util.List<GameState.Notice> eat = game.drainNotices();
        check(eat.stream().anyMatch(n -> n.kind() == GameState.Notice.Kind.STOCK),
                "eating the 📈 did not fire a STOCK notice (no portfolio share bought)");
        check(interlude == null, "a stock wrongly opened an interlude");
        eat.forEach(this::onNotice); // exercise the app's rally toast/juice
        playRenderAndSnap("share");
        // A fat portfolio then takes a MARGIN CALL: the CRASH notice must fire and the toast path run.
        game.grantShares(9);
        int before = game.shares();
        game.forceCrash();
        check(game.shares() == before / 2, "the crash did not halve the portfolio");
        java.util.List<GameState.Notice> ns = game.drainNotices();
        check(ns.stream().anyMatch(n -> n.kind() == GameState.Notice.Kind.CRASH), "no CRASH notice fired");
        ns.forEach(this::onNotice); // exercise the app's margin-call toast/juice
        playRenderAndSnap("crash");
    }

    /**
     * Economy audit for the ~20-min completability target: prove the 📈 rally can build a real
     * multiplier, then project the total time-to-TRUE-ENDING from the (real) price table and explicit,
     * labeled assumptions. It fails if the critical path drifts long enough to blow the ~20-min goal -
     * a regression guard so nobody quietly re-inflates the floor/ending prices. (The full purchase ->
     * descend -> ending CHAIN is proven end-to-end by the `descend` and `ending` scenarios.)
     */
    private void playWin() {
        // 1) The stock mechanic must be able to rally: force stocks (walls wrap, tail trimmed, so a
        //    straight walker never dies) and record the peak multiplier reached.
        beginPlay(11L);
        game.clearObstacles();
        game.setStocksEnabled(true);
        game.setWrapUntilLevel(999); // wrap the edges so a straight walker never crashes on a wall
        game.setTrimPerLevel(5);     // keep length short so the wrap path never self-collides
        double peak = 1.0;
        for (int n = 0; n < 40 && game.status() == GameState.Status.RUNNING; n++) {
            game.forceStockFood(ahead());
            step();
            peak = Math.max(peak, game.stockMultiplier());
        }
        check(peak > 1.5, "the 📈 rally never built a meaningful multiplier (peak " + peak + ")");
        playRenderAndSnap("rally");

        // 2) Critical path to the TRUE ENDING: descend every floor + buy the ending (BARBER is optional).
        //    Read the LIVE prices from StoreInterlude so this guard tracks any real price change - if
        //    the numbers are re-inflated there, the projection below actually catches it.
        int floorSum = StoreInterlude.floorPriceSum();
        int critical = StoreInterlude.criticalPathScore();

        // 3) Project total minutes with explicit assumptions, for a no-stock worst case and a modest
        //    rally. score/food folds in the +5 gem (~1 per 5 food, ~half grabbed); s/food is competent
        //    play (travel + the speed ramp); overhead is warmups + novels + chapters + death/nav slack.
        double bonus = 1.5;
        double secPerFood = 1.35;
        double overheadMin = 3.0 /*2 warmups*/ + 4.0 /*5 novels*/ + 1.5 /*5 chapters*/ + 3.0 /*slack*/;
        double worstMin = critical / (bonus * 1.0) * secPerFood / 60.0 + overheadMin;
        double typicalMin = critical / (bonus * 1.3) * secPerFood / 60.0 + overheadMin;

        System.out.printf(java.util.Locale.ROOT, "%n=== ECONOMY (win-path projection) ===%n");
        System.out.printf(java.util.Locale.ROOT, "  critical-path score: %d  (floors %d + ending %d; barber optional QoL)%n",
                critical, floorSum, StoreInterlude.endingPrice());
        System.out.printf(java.util.Locale.ROOT, "  peak 📈 multiplier reached: %.2fx%n", peak);
        System.out.printf(java.util.Locale.ROOT,
                "  projected time-to-ending: ~%.0f min (no-stock worst) / ~%.0f min (modest rally)%n",
                worstMin, typicalMin);
        System.out.printf(java.util.Locale.ROOT,
                "  assumptions: %.1f score/food, %.2f s/food, %.0f min interlude+warmup+slack%n",
                bonus, secPerFood, overheadMin);

        check(worstMin <= 25.0, "worst-case time-to-win too long (" + Math.round(worstMin) + " min)");
        check(typicalMin <= 21.0, "typical time-to-win drifted past the ~20-min target ("
                + Math.round(typicalMin) + " min)");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
