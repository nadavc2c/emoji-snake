package com.emojisnake.mode;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * Two related "there's a whole game under this one" reveals, both paged on keypress ("press any key to
 * progress") so the lines are readable:
 * <ul>
 *   <li>the ↓↓↓ gag ({@code new SecretInterlude()}): the original sub-basement / "it was always
 *       billing" bit;</li>
 *   <li>a back-room <b>descent chapter</b> ({@code new SecretInterlude(floor, maxFloors)}): the
 *       floor-specific lore shown when you buy a descent, escalating toward the CEO ending.</li>
 * </ul>
 */
public final class SecretInterlude implements Interlude {

    private record Beat(String big, String small) { }

    private static final Beat[] GAG = {
        new Beat("SUB-BASEMENT - UNLOCKED",
                "there is always a floor beneath the firm. you found the stairs."),
        new Beat("descending past Legal, past Compliance...",
                "...past the floors that don't have names, only invoices."),
        new Beat("THE REAL GAME WAS BILLING.",
                "it always was. press any key - you'll be charged for it."),
    };

    private static final String[] FLOOR_TITLE = {
        "SUB-LEVEL 1 - THE MAILROOM",
        "SUB-LEVEL 2 - COMPLIANCE",
        "SUB-LEVEL 3 - THE LITIGATION PITS",
        "SUB-LEVEL 4 - THE PARTNERS' DEN",
        "SUB-LEVEL 5 - THE MANAGING PARTNER'S OFFICE",
    };
    private static final String[] FLOOR_FLAVOR = {
        "every envelope is a threat somebody paid to send. you file them, and you learn the names.",
        "'compliance' is a verb down here. they comply people - quietly, on paper, permanently.",
        "the motions you lost are still breathing in the dark. a deposition that bites back.",
        "the partners smell the ambition coming off you through the floor. they approve. that's worse.",
        "the managing partner's chair sits empty behind the widest desk in the firm. the brass plate is already engraved with your name.",
    };

    private final Beat[] beats;
    private int beat;
    private double elapsed;
    private boolean dismissed;

    /** The ↓↓↓ Frog-Fractions gag. */
    public SecretInterlude() {
        this.beats = GAG;
    }

    /** A back-room descent chapter for {@code floor} of {@code maxFloors} (1-based). */
    public SecretInterlude(int floor, int maxFloors) {
        int i = Math.max(1, Math.min(floor, FLOOR_TITLE.length)) - 1;
        Beat close = floor >= maxFloors
                ? new Beat("THE FIRM HAS NO DEEPER FLOOR.",
                        "you've proven yourself in the dark. the only way left now is UP - to the chair with your name on it.")
                : new Beat("one floor deeper.",
                        "the firm feels you moving through its foundations. it does not object. keep descending.");
        this.beats = new Beat[] {
            new Beat("FLOOR " + floor + "/" + maxFloors + " - " + FLOOR_TITLE[i], FLOOR_FLAVOR[i]),
            close,
        };
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
            return; // mute / screenshot / etc. must not page past a line you're still reading
        }
        beat++;
        if (beat >= beats.length) {
            dismissed = true;
        }
    }

    @Override
    public void render(GraphicsContext gc, double w, double h) {
        gc.setFill(Color.web("#05030a"));
        gc.fillRect(0, 0, w, h);
        gc.setTextAlign(TextAlignment.CENTER);

        Beat b = beats[Math.min(beat, beats.length - 1)];
        boolean last = beat >= beats.length - 1;
        double maxWidth = w - 40;

        gc.setFont(fit(b.big(), 34, FontWeight.BOLD, maxWidth));
        gc.setFill(((int) (elapsed * 6)) % 2 == 0 ? Color.web("#7ee081") : Color.web("#7df9ff"));
        gc.fillText(b.big(), w / 2, h / 2 - 28);

        gc.setFont(fit(b.small(), 18, FontWeight.NORMAL, maxWidth));
        gc.setFill(Color.web("#9ad29a"));
        gc.fillText(b.small(), w / 2, h / 2 + 14);

        double dotY = h / 2 + 64;
        for (int i = 0; i < beats.length; i++) {
            gc.setFill(i <= beat ? Color.web("#ffd54a") : Color.web("#3a3450"));
            gc.fillOval(w / 2 - (beats.length * 9) / 2.0 + i * 18, dotY, 9, 9);
        }

        if (((int) (elapsed * 2)) % 2 == 0) {
            gc.setFont(Font.font("Consolas", FontWeight.BOLD, 16));
            gc.setFill(Color.web("#8a948a"));
            gc.fillText(last ? "[ press a key to leave ]" : "[ press a key to continue ]",
                    w / 2, h - 48);
        }
    }

    /** Shrink the font until {@code text} fits {@code maxWidth}, so no line ever crops at the edges. */
    private static Font fit(String text, double base, FontWeight weight, double maxWidth) {
        Font f = Font.font("Consolas", weight, base);
        javafx.scene.text.Text probe = new javafx.scene.text.Text(text);
        probe.setFont(f);
        double measured = probe.getLayoutBounds().getWidth();
        return measured > maxWidth ? Font.font("Consolas", weight, base * maxWidth / measured) : f;
    }
}
