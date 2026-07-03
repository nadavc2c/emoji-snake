package com.emojisnake.mode;

import com.emojisnake.Direction;
import com.emojisnake.EmojiAtlas;
import com.emojisnake.Tile;
import java.util.Random;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * The slot-machine gag: eating a 🎰 suspends the snake and spins three reels of emoji symbols that
 * stop one by one, then reveals how much LENGTH you won (a gamble - a measly +1 or a jackpot). The
 * app applies the payout via {@code game.addPendingGrowth(amount())} when the interlude finishes.
 */
public final class SlotInterlude implements Interlude {

    private static final Tile[] REEL = {
        Tile.FOOD_APPLE, Tile.FOOD_PIZZA, Tile.FOOD_DONUT, Tile.FIRE, Tile.GOLD, Tile.GHOST, Tile.BONUS,
    };
    private static final double[] STOP = {1.2, 1.8, 2.4}; // when each reel locks
    private static final double REVEAL = 2.8;
    private static final int MAX = 6;

    private final EmojiAtlas atlas;
    private final Random rng = new Random();
    private final int amount = 1 + rng.nextInt(MAX); // 1..6 length won
    private final Tile[] locked = new Tile[3];

    private double elapsed;
    private boolean dismissed;
    private Direction exitDir;

    public SlotInterlude(EmojiAtlas atlas) {
        this.atlas = atlas;
        if (amount >= 5) {
            locked[0] = locked[1] = locked[2] = Tile.GOLD; // jackpot reads as three of a kind
        } else {
            for (int i = 0; i < 3; i++) {
                locked[i] = REEL[rng.nextInt(REEL.length)];
            }
        }
    }

    public int amount() {
        return amount;
    }

    /** The heading the player left on, if they dismissed with a direction key (else null). */
    public Direction exitDirection() {
        return exitDir;
    }

    @Override
    public void update(double dt) {
        elapsed += dt;
    }

    @Override
    public boolean isDone() {
        return dismissed; // wait for the player - never auto-resume off a timer
    }

    @Override
    public void handleKey(KeyCode code) {
        if (elapsed <= REVEAL) {
            return; // can't skip the spin; reels must finish and the win must show
        }
        if (!Interlude.isAdvanceKey(code)) {
            return; // mute / screenshot / etc. must not dismiss the win before you've read it
        }
        dismissed = true;
        exitDir = switch (code) {
            case UP, W -> Direction.UP;
            case DOWN, S -> Direction.DOWN;
            case LEFT, A -> Direction.LEFT;
            case RIGHT, D -> Direction.RIGHT;
            default -> null;
        };
    }

    @Override
    public void render(GraphicsContext gc, double w, double h) {
        gc.setFill(Color.web("#140a1e"));
        gc.fillRect(0, 0, w, h);
        double hue = (elapsed * 90) % 360;

        // Marquee.
        atlas.draw(gc, Tile.SLOT, 40, 40, 44);
        atlas.draw(gc, Tile.SLOT, w - 84, 40, 44);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 30));
        gc.setFill(Color.hsb(hue, 0.7, 1.0));
        gc.fillText("LUCKY SLITHER SLOTS", w / 2, 72);

        // Reels.
        double size = 96;
        double gap = 26;
        double total = size * 3 + gap * 2;
        double x0 = w / 2 - total / 2;
        double y = h / 2 - size / 2;
        for (int i = 0; i < 3; i++) {
            double rx = x0 + i * (size + gap);
            gc.setFill(Color.web("#0a0610"));
            gc.fillRoundRect(rx - 8, y - 8, size + 16, size + 16, 14, 14);
            gc.setStroke(Color.hsb((hue + i * 40) % 360, 0.85, 1.0));
            gc.setLineWidth(3);
            gc.strokeRoundRect(rx - 8, y - 8, size + 16, size + 16, 14, 14);
            Tile sym = elapsed < STOP[i]
                    ? REEL[(int) Math.floorMod((long) (elapsed * 22 + i * 3), REEL.length)]
                    : locked[i];
            atlas.draw(gc, sym, rx, y, size);
        }

        // Status / reveal.
        if (elapsed >= REVEAL) {
            boolean jackpot = amount >= 5;
            gc.setFont(Font.font("Consolas", FontWeight.BOLD, 38));
            gc.setFill(jackpot ? Color.web("#ffd54a") : Color.web("#7ee081"));
            gc.fillText((jackpot ? "JACKPOT!  +" : "+") + amount + " LENGTH", w / 2, y + size + 92);
            gc.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 16));
            gc.setFill(Color.web("#9fb39f"));
            gc.fillText("press a key to collect", w / 2, y + size + 126);
        } else {
            gc.setFont(Font.font("Consolas", FontWeight.BOLD, 22));
            gc.setFill(Color.web("#ff9ec7"));
            gc.fillText("spinning…", w / 2, y + size + 92);
        }

        // Neon frame.
        gc.setStroke(Color.hsb(hue, 0.85, 1.0, 0.9));
        gc.setLineWidth(4);
        gc.strokeRect(3, 3, w - 6, h - 6);
    }
}
