package com.emojisnake.fx;

import com.emojisnake.IntensitySnapshot;
import java.util.Random;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * The in-window "things are going wrong" layer: timed glitch bursts (inverted/neon bands,
 * RGB-torn slices) and occasional self-aware hostile text, all rendered on the overlay canvas -
 * pure illusion, never touches real files. Burst frequency and intensity ramp with the
 * corruption level, so the cute surface visibly rots as a run escalates (the DDLC drain, tuned
 * for the eye-bleed vibe).
 *
 * <p>Also provides HUD-degradation helpers ({@link #garble}, {@link #jitter}) so the score/level
 * text starts glitching at high corruption. Purely cosmetic, so it's verified via {@code
 * --snapshot}, not unit tests.
 */
public final class GlitchDirector {

    private static final char[] GARBAGE = "#@%&!?*▓▒░█§¶".toCharArray();
    // Dark / cruel / depressive-funny - Undertale's voice, drained of the friendship.
    private static final String[] HOSTILE = {
        "WHY BOTHER", "IT ENDS THE SAME", "YOU WILL LOSE", "NO ONE IS WATCHING",
        "KEEP CRAWLING", "THIS IS ALL THERE IS", "ARE YOU NUMB YET", "AGAIN. AND AGAIN.",
        "DO YOU EVEN WANT THIS", "STILL HERE. why", "JOY IS A BUG", "FEED THE VOID", "HOLLOW"
    };

    private final double width;
    private final double height;
    private final double top;
    private final Random rng = new Random();

    private double cooldown = 2.0;   // seconds until the next burst
    private double burstLeft;        // seconds remaining in the current burst
    private double messageLeft;      // seconds remaining for the hostile message
    private String message = "";
    private double corruption;
    private boolean metaUnlocked;    // the whole layer stays dormant for the first runs

    public GlitchDirector(double width, double height, double boardTop) {
        this.width = width;
        this.height = height;
        this.top = boardTop;
    }

    /** The deranged layer only "wakes up" after the first couple of games (the roguelite reveal). */
    public void setMetaUnlocked(boolean unlocked) {
        this.metaUnlocked = unlocked;
    }

    public void update(IntensitySnapshot snap, double dt) {
        corruption = snap.corruption();
        if (!metaUnlocked || corruption < 0.15) {
            burstLeft = messageLeft = 0;
            return; // dormant (early runs) or still-pristine surface - no glitches yet
        }

        burstLeft = Math.max(0, burstLeft - dt);
        messageLeft = Math.max(0, messageLeft - dt);

        cooldown -= dt;
        if (cooldown <= 0) {
            burstLeft = 0.06 + rng.nextDouble() * 0.16;
            // More corruption -> bursts come far more often.
            double interval = lerp(6.0, 0.4, corruption);
            cooldown = interval * (0.5 + rng.nextDouble());
            // Rarely, escalate into a self-aware taunt (more likely the worse it gets).
            if (rng.nextDouble() < 0.15 + corruption * 0.35) {
                message = HOSTILE[rng.nextInt(HOSTILE.length)];
                messageLeft = 0.5 + rng.nextDouble() * 0.8;
            }
        }
    }

    /** Draw active glitch bands + hostile text onto the overlay (called each frame). */
    public void render(GraphicsContext gc) {
        if (burstLeft > 0) {
            int bands = 2 + rng.nextInt(4 + (int) (corruption * 6));
            for (int i = 0; i < bands; i++) {
                double y = top + rng.nextDouble() * (height - top);
                double h = 3 + rng.nextDouble() * (10 + corruption * 26);
                if (rng.nextBoolean()) {
                    // Inverted tear.
                    gc.setGlobalBlendMode(BlendMode.DIFFERENCE);
                    gc.setGlobalAlpha(0.6 + 0.4 * corruption);
                    gc.setFill(Color.WHITE);
                } else {
                    // Neon RGB slice.
                    gc.setGlobalBlendMode(BlendMode.SCREEN);
                    gc.setGlobalAlpha(0.5 + 0.5 * corruption);
                    gc.setFill(Color.hsb(rng.nextInt(360), 1, 1));
                }
                double xShift = (rng.nextDouble() * 2 - 1) * corruption * 14;
                gc.fillRect(xShift, y, width, h);
            }
            gc.setGlobalAlpha(1.0);
            gc.setGlobalBlendMode(BlendMode.SRC_OVER);
        }

        if (messageLeft > 0) {
            double jx = (rng.nextDouble() * 2 - 1) * 4;
            double jy = (rng.nextDouble() * 2 - 1) * 4;
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setFont(TextFit.fit(message, "Consolas", FontWeight.BOLD, 34, width - 40));
            gc.setGlobalBlendMode(BlendMode.DIFFERENCE);
            gc.setFill(Color.hsb(rng.nextInt(360), 1, 1));
            gc.fillText(message, width / 2.0 + jx, top + (height - top) * 0.32 + jy);
            gc.setGlobalBlendMode(BlendMode.SRC_OVER);
        }
    }

    /** Corrupt a HUD string - replaces some characters with garbage at high corruption. */
    public String garble(String text) {
        if (!metaUnlocked || corruption < 0.5) {
            return text;
        }
        double p = (corruption - 0.5) * 0.6; // up to ~0.3 of chars at full corruption
        char[] cs = text.toCharArray();
        for (int i = 0; i < cs.length; i++) {
            if (cs[i] != ' ' && rng.nextDouble() < p) {
                cs[i] = GARBAGE[rng.nextInt(GARBAGE.length)];
            }
        }
        return new String(cs);
    }

    /** Small positional jitter for HUD text once corruption is high (0 below the threshold). */
    public double jitter() {
        if (!metaUnlocked || corruption < 0.5) {
            return 0;
        }
        return (rng.nextDouble() * 2 - 1) * corruption * 3;
    }

    private static double lerp(double a, double b, double t) {
        t = t < 0 ? 0 : (t > 1 ? 1 : t);
        return a + (b - a) * t;
    }
}
