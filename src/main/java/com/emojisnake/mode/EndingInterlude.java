package com.emojisnake.mode;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * The roguelite finale, reached by clearing every back-room floor and buying the way out. You don't
 * escape the firm - you BECOME it: the apex super-partner, the CEO snake. Paged on keypress over the
 * generated CEO portrait ({@code /vn/bg/ending.png}, loaded leniently via {@link VnArt}). When the
 * last beat is dismissed the app ends the run on a win screen (the meta progress persists).
 */
public final class EndingInterlude implements Interlude {

    private record Beat(String big, String small) { }

    private static final Beat[] BEATS = {
        new Beat("THE FIRM HAS NO DEEPER FLOOR.",
                "you crawled all the way to the bottom. so the private elevator only goes one way now: up."),
        new Beat("THE FIRM RECOGNISES ITS HEIR.",
                "it carries you past every floor you bled on - straight up to the corner office in the sky."),
        new Beat("YOU ARE THE SUPER PARTNER.",
                "CEO. apex predator. the chair at the top of the tower that every associate bills toward. ♡"),
        new Beat("THE SNAKE ATE EVERYTHING.",
                "the firm, the floors, the ending itself. press a key to keep grinding."),
    };

    private final VnArt art;
    private int beat;
    private double elapsed;
    private boolean dismissed;

    public EndingInterlude(VnArt art) {
        this.art = art;
    }

    @Override
    public void update(double dt) {
        elapsed += dt;
    }

    @Override
    public boolean isDone() {
        return dismissed;
    }

    @Override
    public void handleKey(KeyCode code) {
        if (!Interlude.isAdvanceKey(code)) {
            return; // only a play key pages the finale (not mute / screenshot / etc.)
        }
        beat++;
        if (beat >= BEATS.length) {
            dismissed = true;
        }
    }

    @Override
    public void render(GraphicsContext gc, double w, double h) {
        Image img = art != null ? art.background("ending") : null;
        if (img != null) {
            gc.drawImage(img, 0, 0, w, h);
        } else {
            gc.setFill(Color.web("#0a0510"));
            gc.fillRect(0, 0, w, h);
        }
        gc.setFill(Color.color(0, 0, 0, 0.55)); // scrim under the text
        gc.fillRect(0, h * 0.62, w, h * 0.38);
        double hue = (elapsed * 40) % 360;
        gc.setLineWidth(4);
        gc.setStroke(Color.hsb(hue, 0.85, 1.0, 0.9));
        gc.strokeRect(3, 3, w - 6, h - 6);

        Beat b = BEATS[Math.min(beat, BEATS.length - 1)];
        boolean last = beat >= BEATS.length - 1;
        double maxWidth = w - 40;

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(fit(b.big(), 30, FontWeight.BOLD, maxWidth));
        gc.setFill(Color.web("#ffd54a"));
        gc.fillText(b.big(), w / 2, h * 0.72);
        gc.setFont(fit(b.small(), 17, FontWeight.NORMAL, maxWidth));
        gc.setFill(Color.web("#eaf4ea"));
        gc.fillText(b.small(), w / 2, h * 0.78);

        for (int i = 0; i < BEATS.length; i++) {
            gc.setFill(i <= beat ? Color.web("#ffd54a") : Color.web("#3a3450"));
            gc.fillOval(w / 2 - (BEATS.length * 9) / 2.0 + i * 18, h * 0.84, 9, 9);
        }
        if (((int) (elapsed * 2)) % 2 == 0) {
            gc.setFont(Font.font("Consolas", FontWeight.BOLD, 15));
            gc.setFill(Color.web("#9ad29a"));
            gc.fillText(last ? "[ press a key to keep grinding ]" : "[ press a key ]", w / 2, h - 34);
        }
    }

    private static Font fit(String text, double base, FontWeight weight, double maxWidth) {
        Font f = Font.font("Consolas", weight, base);
        javafx.scene.text.Text probe = new javafx.scene.text.Text(text);
        probe.setFont(f);
        double measured = probe.getLayoutBounds().getWidth();
        return measured > maxWidth ? Font.font("Consolas", weight, base * maxWidth / measured) : f;
    }
}
