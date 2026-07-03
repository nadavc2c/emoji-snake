package com.emojisnake.mode;

import com.emojisnake.EmojiAtlas;
import com.emojisnake.Tile;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * The genre-shift boss fight: Snake suddenly becomes a bottom-row bullet-hell. A 👹 rains 🔴
 * projectiles; steer left/right to dodge for the duration. Each hit costs a shared life (brief
 * i-frames after). Survive and you're rewarded; run out of lives and the run ends. A sequential
 * genre <em>shift</em> (not a fusion) that hands control back when finished.
 */
public final class DodgeBossInterlude implements Interlude {

    private static final double DURATION = 11.0;
    private static final double PLAYER_STEP = 30;
    private static final double PLAYER_SIZE = 34;
    private static final double SHOT_SIZE = 22;

    private final EmojiAtlas atlas;
    private final double width;
    private final double height;
    private final int startingLives;
    private final Random rng = new Random();

    private final List<double[]> shots = new ArrayList<>(); // {x, y, vy}
    private double playerX;
    private double elapsed;
    private double spawnTimer = 0.6;
    private double iFrame;
    private int hits;

    public DodgeBossInterlude(EmojiAtlas atlas, double width, double height, int startingLives) {
        this.atlas = atlas;
        this.width = width;
        this.height = height;
        this.startingLives = Math.max(1, startingLives);
        this.playerX = width / 2;
    }

    @Override
    public void update(double dt) {
        elapsed += dt;
        if (iFrame > 0) iFrame -= dt;

        // Spawn rate ramps up over the fight.
        spawnTimer -= dt;
        double interval = Math.max(0.12, 0.4 - elapsed * 0.02);
        if (spawnTimer <= 0) {
            spawnTimer = interval;
            double x = 30 + rng.nextDouble() * (width - 60);
            double vy = 150 + rng.nextDouble() * 90 + elapsed * 8;
            shots.add(new double[] {x, 90, vy});
        }

        double playerY = height - 60;
        for (int i = shots.size() - 1; i >= 0; i--) {
            double[] s = shots.get(i);
            s[1] += s[2] * dt;
            if (s[1] > height + 20) {
                shots.remove(i);
                continue;
            }
            if (iFrame <= 0) {
                double dx = s[0] - playerX;
                double dy = s[1] - playerY;
                if (dx * dx + dy * dy < (PLAYER_SIZE * 0.5 + SHOT_SIZE * 0.5) * (PLAYER_SIZE * 0.5 + SHOT_SIZE * 0.5)) {
                    hits++;
                    iFrame = 1.0;
                    shots.remove(i);
                }
            }
        }
    }

    @Override
    public void handleKey(KeyCode code) {
        if (code == KeyCode.LEFT || code == KeyCode.A) {
            playerX = Math.max(PLAYER_SIZE / 2, playerX - PLAYER_STEP);
        } else if (code == KeyCode.RIGHT || code == KeyCode.D) {
            playerX = Math.min(width - PLAYER_SIZE / 2, playerX + PLAYER_STEP);
        }
    }

    @Override
    public boolean isDone() {
        return elapsed >= DURATION || hits >= startingLives;
    }

    public int hits() {
        return hits;
    }

    @Override
    public void render(GraphicsContext gc, double w, double h) {
        gc.setFill(Color.web("#0a0712"));
        gc.fillRect(0, 0, w, h);

        // Title + countdown bar.
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 22));
        gc.setFill(Color.web("#ff5a6a"));
        gc.fillText("IT WOKE UP.  DODGE.", w / 2, 28);
        double frac = Math.max(0, 1 - elapsed / DURATION);
        gc.setFill(Color.web("#33203a"));
        gc.fillRect(40, 44, w - 80, 8);
        gc.setFill(Color.web("#7ee081"));
        gc.fillRect(40, 44, (w - 80) * (1 - frac), 8);

        // Boss, bobbing.
        double bossX = w / 2 + Math.sin(elapsed * 2) * (w * 0.25);
        atlas.draw(gc, Tile.BOSS, bossX - 28, 60, 56);

        // Projectiles.
        for (double[] s : shots) {
            atlas.draw(gc, Tile.PROJECTILE, s[0] - SHOT_SIZE / 2, s[1] - SHOT_SIZE / 2, SHOT_SIZE);
        }

        // Player (flicker during i-frames).
        if (iFrame <= 0 || ((int) (iFrame * 12)) % 2 == 0) {
            atlas.draw(gc, Tile.HEAD, playerX - PLAYER_SIZE / 2, h - 60 - PLAYER_SIZE / 2,
                    PLAYER_SIZE);
        }

        // Lives remaining.
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        gc.setFill(Color.web("#ff5a6a"));
        int left = Math.max(0, startingLives - hits);
        gc.fillText("♥".repeat(left), 16, h - 16);
    }
}
