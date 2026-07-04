package com.emojisnake.mode;

import com.emojisnake.EmojiAtlas;
import com.emojisnake.Tile;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * The <b>Buchmann</b> boss - the second genre-shift fight (named after TLV University's Buchmann Faculty
 * of Law). Snake becomes <b>Pac-Man</b>: you are the yellow chomper loose in a compact maze, devouring
 * the "curriculum" (pellets) while three 👻 senior partners hunt you. Clear every pellet to
 * <b>graduate</b> (a flawless bonus); a partner catching you costs a shared life (with brief i-frames);
 * run out of lives - or out of time - and the fight ends and hands control back.
 *
 * <p>The maze is a generated pillar lattice (border walls + a regular grid of single-cell pillars), so
 * every pellet is guaranteed reachable. Pac-Man is drawn with {@code fillArc} (no new emoji asset);
 * ghosts reuse {@link Tile#GHOST}. Mirrors {@link DodgeBossInterlude}'s shape: {@code hits()} +
 * {@code cleared()} results and {@code forceHits}/{@code forceCleared} seams for {@code --play:buchmann}.
 */
public final class BuchmannBossInterlude implements Interlude {

    private static final double DURATION = 24.0;    // safety cap; the fight normally ends on clear/lives
    private static final double PLAYER_STEP = 0.12;  // seconds per player grid-move (a touch faster than ghosts)
    private static final double GHOST_STEP = 0.20;   // seconds per ghost grid-move
    private static final double IFRAME = 1.2;        // grace after a catch
    private static final double START_GRACE = 1.0;   // ghosts hold still briefly so you can orient
    private static final int COLS = 11;
    private static final int ROWS = 9;

    private final EmojiAtlas atlas;
    private final double width;
    private final double height;
    private final int startingLives;
    private final Random rng = new Random();

    private final boolean[][] wall = new boolean[ROWS][COLS];
    private final boolean[][] pellet = new boolean[ROWS][COLS];
    private int pelletsLeft;
    private final List<int[]> ghosts = new ArrayList<>(); // {r, c, homeR, homeC, dr, dc}

    private int pr;
    private int pc;
    private int fdr;   // facing (0,0 = stopped)
    private int fdc;
    private int qdr;   // queued turn
    private int qdc;

    private double elapsed;
    private double stepAcc;
    private double ghostAcc;
    private double iFrame;
    private int hits;
    private boolean cleared;

    public BuchmannBossInterlude(EmojiAtlas atlas, double width, double height, int startingLives) {
        this.atlas = atlas;
        this.width = width;
        this.height = height;
        this.startingLives = Math.max(1, startingLives);
        buildMaze();
    }

    /** Border walls + a lattice of single-cell pillars at even/even interior cells (always fully connected). */
    private void buildMaze() {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                boolean border = r == 0 || r == ROWS - 1 || c == 0 || c == COLS - 1;
                boolean pillar = r % 2 == 0 && c % 2 == 0;
                wall[r][c] = border || pillar;
                pellet[r][c] = !wall[r][c];
            }
        }
        // Player starts centre-bottom; that cell's pellet is already eaten.
        pr = ROWS - 2;
        pc = COLS / 2;
        pellet[pr][pc] = false;
        // Three "senior partners" spawn around the centre (open cells; they keep their pellets).
        addGhost(ROWS / 2, COLS / 2);
        addGhost(ROWS / 2, COLS / 2 - 2);
        addGhost(ROWS / 2, COLS / 2 + 2);
        int count = 0;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (pellet[r][c]) {
                    count++;
                }
            }
        }
        pelletsLeft = count;
    }

    private void addGhost(int r, int c) {
        if (!wall[r][c]) {
            ghosts.add(new int[] {r, c, r, c, 0, 0});
        }
    }

    private boolean walkable(int r, int c) {
        return r >= 0 && r < ROWS && c >= 0 && c < COLS && !wall[r][c];
    }

    @Override
    public void update(double dt) {
        elapsed += dt;
        if (iFrame > 0) {
            iFrame -= dt;
        }

        // Player: grid-stepped on a timer. Apply the queued turn when it opens up, else glide on.
        stepAcc += dt;
        while (stepAcc >= PLAYER_STEP) {
            stepAcc -= PLAYER_STEP;
            if ((qdr != 0 || qdc != 0) && walkable(pr + qdr, pc + qdc)) {
                fdr = qdr;
                fdc = qdc;
            }
            if ((fdr != 0 || fdc != 0) && walkable(pr + fdr, pc + fdc)) {
                pr += fdr;
                pc += fdc;
                if (pellet[pr][pc]) {
                    pellet[pr][pc] = false;
                    pelletsLeft--;
                    if (pelletsLeft <= 0) {
                        cleared = true;
                    }
                }
            }
        }

        // Graduation outranks a same-tick catch: if the last pellet was just eaten, end clean (no hit).
        if (cleared) {
            return;
        }
        // Catch the case where PAC-MAN stepped onto a partner - incl. a head-on swap (checked before
        // the partners move, while the partner is still on the cell Pac-Man just entered).
        checkCatch();

        // Ghosts start chasing after a brief grace so the player can orient.
        if (elapsed >= START_GRACE) {
            ghostAcc += dt;
            while (ghostAcc >= GHOST_STEP) {
                ghostAcc -= GHOST_STEP;
                stepGhosts();
            }
        }

        // ...and the case where a partner stepped onto PAC-MAN.
        checkCatch();
    }

    /** A partner sharing Pac-Man's cell (outside i-frames) costs a life and sends every partner home. */
    private void checkCatch() {
        if (iFrame > 0) {
            return;
        }
        for (int[] g : ghosts) {
            if (g[0] == pr && g[1] == pc) {
                hits++;
                iFrame = IFRAME;
                for (int[] gg : ghosts) {
                    gg[0] = gg[2];
                    gg[1] = gg[3];
                    gg[4] = 0;
                    gg[5] = 0;
                }
                return;
            }
        }
    }

    private void stepGhosts() {
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] g : ghosts) {
            List<int[]> options = new ArrayList<>();
            for (int[] d : dirs) {
                if (!walkable(g[0] + d[0], g[1] + d[1])) {
                    continue;
                }
                boolean reverse = d[0] == -g[4] && d[1] == -g[5] && (g[4] != 0 || g[5] != 0);
                if (!reverse) {
                    options.add(d);
                }
            }
            if (options.isEmpty()) {
                // dead end: allow the reverse
                for (int[] d : dirs) {
                    if (walkable(g[0] + d[0], g[1] + d[1])) {
                        options.add(d);
                    }
                }
            }
            if (options.isEmpty()) {
                continue;
            }
            int[] choice;
            if (rng.nextDouble() < 0.25) {
                choice = options.get(rng.nextInt(options.size())); // wander -> beatable
            } else {
                choice = options.get(0);
                int best = Integer.MAX_VALUE;
                for (int[] d : options) {
                    int nd = Math.abs(g[0] + d[0] - pr) + Math.abs(g[1] + d[1] - pc);
                    if (nd < best) {
                        best = nd;
                        choice = d;
                    }
                }
            }
            g[0] += choice[0];
            g[1] += choice[1];
            g[4] = choice[0];
            g[5] = choice[1];
        }
    }

    @Override
    public void handleKey(KeyCode code) {
        switch (code) {
            case UP, W -> { qdr = -1; qdc = 0; }
            case DOWN, S -> { qdr = 1; qdc = 0; }
            case LEFT, A -> { qdr = 0; qdc = -1; }
            case RIGHT, D -> { qdr = 0; qdc = 1; }
            default -> { /* ignore */ }
        }
    }

    @Override
    public boolean isDone() {
        return cleared || hits >= startingLives || elapsed >= DURATION;
    }

    public int hits() {
        return hits;
    }

    /** True if every pellet was eaten (you graduated) - the flawless result. */
    public boolean cleared() {
        return cleared;
    }

    /** Self-play seam ({@code --play:buchmann}): end NOW with exactly {@code n} hits, not cleared. */
    public void forceHits(int n) {
        this.hits = Math.max(0, Math.min(startingLives, n));
        this.elapsed = DURATION;
    }

    /** Self-play seam: end NOW as a full clear (graduated). */
    public void forceCleared() {
        this.cleared = true;
        this.pelletsLeft = 0;
        this.elapsed = DURATION;
    }

    @Override
    public void render(GraphicsContext gc, double w, double h) {
        gc.setFill(Color.web("#05060f"));
        gc.fillRect(0, 0, w, h);

        // Title + status.
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 20));
        gc.setFill(Color.web("#ffd54a"));
        gc.fillText("BUCHMANN FACULTY - DEVOUR THE CURRICULUM", w / 2, 26);
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 14));
        gc.setFill(Color.web("#7df9ff"));
        int left = Math.max(0, startingLives - hits);
        gc.fillText("credits left: " + pelletsLeft + "     " + "♥".repeat(left), w / 2, 48);

        // Maze geometry: centred, square cells.
        double topMargin = 64;
        double botMargin = 20;
        double cell = Math.min(w / COLS, (h - topMargin - botMargin) / ROWS);
        double ox = (w - cell * COLS) / 2;
        double oy = topMargin;

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                double x = ox + c * cell;
                double y = oy + r * cell;
                if (wall[r][c]) {
                    gc.setFill(Color.web("#1b2a6b"));
                    gc.fillRoundRect(x + 2, y + 2, cell - 4, cell - 4, 8, 8);
                } else if (pellet[r][c]) {
                    gc.setFill(Color.web("#ffd7a0"));
                    double d = Math.max(3, cell * 0.14);
                    gc.fillOval(x + cell / 2 - d / 2, y + cell / 2 - d / 2, d, d);
                }
            }
        }

        // Ghosts (senior partners).
        for (int[] g : ghosts) {
            double gx = ox + g[1] * cell;
            double gy = oy + g[0] * cell;
            atlas.draw(gc, Tile.GHOST, gx + cell * 0.1, gy + cell * 0.1, cell * 0.8);
        }

        // Pac-Man: an animated yellow wedge facing its heading (flickers during i-frames).
        if (iFrame <= 0 || ((int) (iFrame * 12)) % 2 == 0) {
            double px = ox + pc * cell + cell / 2;
            double py = oy + pr * cell + cell / 2;
            double rad = cell * 0.42;
            double mouth = 8 + 32 * (0.5 + 0.5 * Math.sin(elapsed * 14)); // half-angle of the open mouth
            double facing = facingAngle();
            gc.setFill(Color.web("#ffe23a"));
            gc.fillArc(px - rad, py - rad, rad * 2, rad * 2, facing + mouth, 360 - 2 * mouth, ArcType.ROUND);
        }
    }

    /** Canvas-arc angle (degrees) the mouth-gap points toward, given the facing. Canvas y is DOWN,
     *  so fillArc's +90° points down-screen and +270° points up-screen (0/180 on the x-axis are unflipped). */
    private double facingAngle() {
        if (fdc > 0) return 0;      // right
        if (fdc < 0) return 180;    // left
        if (fdr < 0) return 270;    // up
        if (fdr > 0) return 90;     // down
        return 0;                   // stopped -> face right
    }
}
