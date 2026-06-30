package com.emojisnake.mode;

import java.util.Random;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * The DDLC-style fake crash - an in-window illusion only (it never touches real files or spawns
 * real OS dialogs). It paints a fake fatal-error console for a few seconds, then admits it was
 * lying and hands control back. Pure theatre for the deranged reveal.
 */
public final class FakeCrashInterlude implements Interlude {

    private static final String[] TRACE = {
        "Exception in thread \"main\" java.lang.RealityError: you should not be here",
        "    at com.emojisnake.Void.consume(Void.java:0)",
        "    at com.emojisnake.Snake.crawl(Snake.java:404)",
        "    at com.emojisnake.You.persist(You.java:∞)",
        "    at java.base/java.lang.Hope.<clinit>(Hope.java:-1)",
        "Caused by: java.lang.NullPointerException: \"meaning\" is null",
        "    ... 1 unreachable frame",
        "",
        "[FATAL] process state: undefined. dumping core of your attention...",
    };

    private final Random rng = new Random();
    private double elapsed;
    private final double traceDuration = 2.6;
    private final double total = 4.0;

    @Override
    public void update(double dt) {
        elapsed += dt;
    }

    @Override
    public void handleKey(KeyCode code) {
        // Any key hurries past the trace, but you can't actually escape it early.
        if (elapsed < traceDuration) {
            elapsed = traceDuration;
        }
    }

    @Override
    public boolean isDone() {
        return elapsed >= total;
    }

    @Override
    public void render(GraphicsContext gc, double width, double height) {
        gc.setFill(Color.web("#05060a"));
        gc.fillRect(0, 0, width, height);

        if (elapsed < traceDuration) {
            int shownLines = (int) Math.min(TRACE.length, (elapsed / traceDuration) * TRACE.length + 1);
            gc.setTextAlign(TextAlignment.LEFT);
            gc.setFont(Font.font("Consolas", FontWeight.NORMAL, 13));
            double y = 40;
            for (int i = 0; i < shownLines; i++) {
                gc.setFill(i == 5 ? Color.web("#ff5a6a") : Color.web("#9ad29a"));
                String line = TRACE[i];
                if (rng.nextDouble() < 0.06) {
                    line = corrupt(line);
                }
                gc.fillText(line, 16, y);
                y += 18;
            }
            // blinking cursor
            if (((int) (elapsed * 3)) % 2 == 0) {
                gc.setFill(Color.web("#9ad29a"));
                gc.fillText("_", 16, y);
            }
        } else {
            double t = elapsed - traceDuration;
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setFont(Font.font("Consolas", FontWeight.BOLD, 40));
            gc.setFill(t < 0.25 && ((int) (t * 20)) % 2 == 0 ? Color.WHITE : Color.web("#7ee081"));
            gc.fillText("...just kidding.", width / 2, height / 2);
            gc.setFont(Font.font("Consolas", FontWeight.NORMAL, 16));
            gc.setFill(Color.web("#8a948a"));
            gc.fillText("you don't get to crash. only i do.", width / 2, height / 2 + 34);
        }
    }

    private String corrupt(String s) {
        char[] cs = s.toCharArray();
        for (int i = 0; i < cs.length; i++) {
            if (cs[i] != ' ' && rng.nextDouble() < 0.12) {
                cs[i] = "#%@&▓░".charAt(rng.nextInt(6));
            }
        }
        return new String(cs);
    }
}
