package com.emojisnake.mode;

import com.emojisnake.Achievements;
import com.emojisnake.Achievements.Achievement;
import java.util.Set;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * The cookie-clicker ACHIEVEMENTS viewer (open with {@code X}). A pause-like takeover that lists the
 * whole {@link Achievements#ALL} catalog as a grid - unlocked ones lit, locked ones dim, and the two
 * secret easter eggs shown as {@code ??? - <hint>} until earned. The header shows how the unlocks have
 * boosted the 📈 stock reward (raised ceiling + crash-proof floor). Drawn purely with the
 * {@link GraphicsContext} (no atlas), so it's headlessly unit-testable. Closes on {@code X}/Esc/Enter/Space.
 */
public final class AchievementsInterlude implements Interlude {

    private final Set<String> unlocked;
    private final int shares;
    private final int cap;
    private final int floor;

    private double elapsed;   // drives the hue-cycling neon border
    private boolean done;

    public AchievementsInterlude(Set<String> unlocked, int shares, int cap, int floor) {
        this.unlocked = unlocked == null ? Set.of() : unlocked;
        this.shares = shares;
        this.cap = cap;
        this.floor = floor;
    }

    @Override
    public void update(double dt) {
        elapsed += dt;
    }

    @Override
    public void handleKey(KeyCode code) {
        switch (code) {
            case X, ESCAPE, ENTER, SPACE -> done = true;
            default -> { /* stay open - a viewer, not a paged reader */ }
        }
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public void render(GraphicsContext gc, double w, double h) {
        gc.setFill(Color.web("#0a0712"));
        gc.fillRect(0, 0, w, h);

        // Hue-cycling neon border (the eye-bleed lives in the surrounds, per the VN convention).
        double hue = (elapsed * 60) % 360;
        gc.setStroke(Color.hsb(hue, 0.85, 1.0));
        gc.setLineWidth(4);
        gc.strokeRect(6, 6, w - 12, h - 12);
        gc.setLineWidth(1);

        gc.setTextBaseline(VPos.CENTER);
        gc.setTextAlign(TextAlignment.CENTER);

        int have = 0;
        for (Achievement a : Achievements.ALL) {
            if (unlocked.contains(a.id())) {
                have++;
            }
        }

        // Header: count + the 📈 reward it has bought.
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 30));
        gc.setFill(Color.web("#ffd54a"));
        gc.fillText("ACHIEVEMENTS  " + have + " / " + Achievements.count(), w / 2, 40);
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 15));
        gc.setFill(Color.web("#5ee06a"));
        gc.fillText("▲ stock ceiling " + cap + "   ·   crash-proof floor " + floor
                + "   ·   holding " + shares, w / 2, 68);

        // Grid of entries: 2 columns, sized to fit the catalog in one screen.
        int n = Achievements.ALL.size();
        int cols = 2;
        int rows = (n + cols - 1) / cols;
        double top = 88;
        double bottom = h - 34;
        double gridH = bottom - top;
        double rowH = gridH / rows;
        double colW = (w - 40) / cols;
        gc.setTextAlign(TextAlignment.LEFT);

        for (int i = 0; i < n; i++) {
            Achievement a = Achievements.ALL.get(i);
            boolean got = unlocked.contains(a.id());
            int col = i / rows;                 // fill column-first so both columns read top-to-bottom
            int row = i % rows;
            double x = 20 + col * colW;
            double y = top + row * rowH;
            double cardW = colW - 12;
            double cardH = rowH - 8;

            // Card background - lit gold-ish when earned, flat dark when not.
            gc.setFill(got ? Color.web("#241d0e") : Color.web("#15121c"));
            gc.fillRect(x, y, cardW, cardH);
            gc.setStroke(got ? Color.web("#ffd54a") : Color.web("#2c2740"));
            gc.strokeRect(x, y, cardW, cardH);

            boolean hide = a.secret() && !got;  // secret + not earned -> mystery
            String marker = got ? "★ " : "  ";       // ★ once earned
            String title = hide ? "???" : a.title();
            String hint = a.hint();

            gc.setTextBaseline(VPos.CENTER);
            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
            gc.setFill(got ? Color.web("#fff3b0") : Color.web("#6c6780"));
            gc.fillText(marker + title, x + 10, y + cardH * 0.36);

            gc.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 12));
            gc.setFill(got ? Color.web("#c9c4a0") : Color.web("#4c4860"));
            gc.fillText(hint, x + 10, y + cardH * 0.72);
        }

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font("Consolas", FontWeight.NORMAL, 13));
        gc.setFill(Color.web("#8a8698"));
        gc.fillText("press X / Esc to close", w / 2, h - 16);
    }
}
