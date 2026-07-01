package com.emojisnake.mode;

import com.emojisnake.Direction;
import com.emojisnake.Element;
import com.emojisnake.EmojiAtlas;
import com.emojisnake.GameState;
import com.emojisnake.StatusKind;
import com.emojisnake.Tile;
import com.emojisnake.fx.TextFit;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * The on-board shop. Currency is your run's score. Two kinds of goods:
 * <ul>
 *   <li><b>This run</b> (consumed): the timed power-ups (1-5).</li>
 *   <li><b>Forever</b> (Dead-Cells meta-progression, persisted by the app): VESTED LIFE (a permanent
 *       +1 max life, capped), THE BARBER (a permanent "haircut" - your tail auto-sheds as you grow,
 *       so runaway length stops being a death sentence; the real enabler for the deep floors, capped),
 *       THE BACK ROOM (descend one persistent floor, rising price), and the TRUE ENDING (a red herring
 *       until every floor is cleared, then the real "become the firm" finale).</li>
 * </ul>
 * Buying a meta item just flags intent ({@link #livesBought()} / {@link #trimBought()} /
 * {@link #boughtSecret()} / {@link #boughtEnding()}); the app applies + persists it in
 * {@code finishInterlude}. Leave on a direction (your resume heading) or ENTER/ESC.
 */
public final class StoreInterlude implements Interlude {

    private static final int STATUS_TICKS = 140;
    private static final int[] FLOOR_PRICE = {35, 80, 150, 260, 420}; // to descend FROM floor i
    private static final int[] LIFE_PRICE = {40, 90};                 // for the 1st / 2nd +max-life
    private static final int[] BARBER_PRICE = {50, 120, 250};         // for the 1st / 2nd / 3rd haircut
    private static final int ENDING_PRICE = 220;

    private final GameState game;
    private final EmojiAtlas atlas;
    private final int rank;          // floors already descended (persistent)
    private final int maxFloors;
    private final int lifeBonus;     // +max-life upgrades already banked (persistent)
    private final int maxLifeBonus;
    private final int trim;          // BARBER "haircut" levels already banked (persistent)
    private final int maxTrim;
    private final int vnDone;        // novels finished (persistent)
    private final int vnTotal;

    private Direction exitDir;
    private boolean boughtSecret;    // bought a descent -> app advances a floor + shows the chapter
    private boolean boughtEnding;    // bought the real ending -> app runs the finale
    private int livesBought;         // permanent +max-life upgrades bought this visit
    private int trimBought;          // permanent +haircut levels bought this visit
    private double elapsed;
    private String flash = "spend your score. some of it even lasts.";
    private double flashT = 3.0;

    public StoreInterlude(GameState game, EmojiAtlas atlas, double width, double height,
                          int rank, int maxFloors, int lifeBonus, int maxLifeBonus,
                          int vnDone, int vnTotal, int trim, int maxTrim) {
        this.game = game;
        this.atlas = atlas;
        this.rank = rank;
        this.maxFloors = maxFloors;
        this.lifeBonus = lifeBonus;
        this.maxLifeBonus = maxLifeBonus;
        this.trim = trim;
        this.maxTrim = maxTrim;
        this.vnDone = vnDone;
        this.vnTotal = vnTotal;
    }

    private static int priceFor(StatusKind s) {
        return switch (s) {
            case GHOST -> 15;
            case GOLD -> 12;
            case BURN -> 8;
            case MAGNET -> 8;
            case SLOW -> 6;
        };
    }

    private int lifePrice() {
        int owned = Math.min(lifeBonus + livesBought, LIFE_PRICE.length - 1);
        return LIFE_PRICE[owned];
    }

    private boolean lifeMaxed() {
        return lifeBonus + livesBought >= maxLifeBonus;
    }

    private int trimPrice() {
        int owned = Math.min(trim + trimBought, BARBER_PRICE.length - 1);
        return BARBER_PRICE[owned];
    }

    private boolean trimMaxed() {
        return trim + trimBought >= maxTrim;
    }

    private int floorPrice() {
        return FLOOR_PRICE[Math.min(rank, FLOOR_PRICE.length - 1)];
    }

    private boolean descendAvailable() {
        return rank < maxFloors;
    }

    private boolean floorsCleared() {
        return rank >= maxFloors;
    }

    private boolean novelsRead() {
        return vnDone >= vnTotal;
    }

    private boolean endingUnlocked() {
        return floorsCleared() && novelsRead(); // both: descend every floor AND read every novel
    }

    @Override
    public void update(double dt) {
        elapsed += dt;
        if (flashT > 0) {
            flashT -= dt;
        }
    }

    @Override
    public boolean isDone() {
        return exitDir != null || boughtSecret || boughtEnding;
    }

    public Direction exitDirection() {
        return exitDir;
    }

    /** True if the player bought a descent (the app advances a floor and shows the chapter). */
    public boolean boughtSecret() {
        return boughtSecret;
    }

    /** True if the player bought the real ending (only possible once all floors are cleared). */
    public boolean boughtEnding() {
        return boughtEnding;
    }

    /** How many permanent +max-life upgrades the player bought this visit. */
    public int livesBought() {
        return livesBought;
    }

    /** How many permanent BARBER "haircut" levels the player bought this visit. */
    public int trimBought() {
        return trimBought;
    }

    @Override
    public void handleKey(KeyCode code) {
        switch (code) {
            case DIGIT1 -> buyPowerUp(0);
            case DIGIT2 -> buyPowerUp(1);
            case DIGIT3 -> buyPowerUp(2);
            case DIGIT4 -> buyPowerUp(3);
            case DIGIT5 -> buyPowerUp(4);
            case DIGIT6 -> buyLife();
            case DIGIT7 -> buyBarber();
            case DIGIT8 -> buyDescend();
            case DIGIT9 -> buyEnding();
            case UP, W -> exitDir = Direction.UP;
            case DOWN, S -> exitDir = Direction.DOWN;
            case LEFT, A -> exitDir = Direction.LEFT;
            case RIGHT, D -> exitDir = Direction.RIGHT;
            case ENTER, SPACE, ESCAPE -> exitDir = game.direction();
            default -> { /* ignore */ }
        }
    }

    private void buyPowerUp(int i) {
        Element e = Element.values()[i];
        int price = priceFor(e.status);
        if (game.score() < price) {
            flash("can't afford " + e.status + ". poverty is a lifestyle choice.");
            return;
        }
        game.addScore(-price);
        game.applyStatus(e.status, STATUS_TICKS);
        flash("bought " + e.status + ". enjoy. no refunds.");
    }

    private void buyLife() {
        if (lifeMaxed()) {
            flash("you're as immortal as the firm allows. greedy.");
            return;
        }
        if (game.score() < lifePrice()) {
            flash("can't afford a vested life. fitting.");
            return;
        }
        game.addScore(-lifePrice());
        livesBought++;
        flash("VESTED. +1 max life, forever. cling to it.");
    }

    private void buyBarber() {
        if (trimMaxed()) {
            flash("your tail's as trim as the firm allows. vanity has limits.");
            return;
        }
        if (game.score() < trimPrice()) {
            flash("can't afford the barber. shaggy it is.");
            return;
        }
        game.addScore(-trimPrice());
        trimBought++;
        flash("SNIP. your tail sheds as you grow, forever. survival is a haircut.");
    }

    private void buyDescend() {
        if (!descendAvailable()) {
            flash("no floors left to descend. the only way out is the TRUE ENDING.");
            return;
        }
        if (game.score() < floorPrice()) {
            flash("the back room isn't free. nothing here is.");
            return;
        }
        game.addScore(-floorPrice());
        boughtSecret = true; // app advances the floor + opens the chapter
    }

    private void buyEnding() {
        if (!endingUnlocked()) {
            flash(!floorsCleared()
                    ? "the TRUE ENDING is for closers. clear all " + maxFloors + " floors first."
                    : "first read all " + vnTotal + " novels. you've finished " + vnDone + ".");
            return;
        }
        if (game.score() < ENDING_PRICE) {
            flash("even ascension has a price. " + ENDING_PRICE + ".");
            return;
        }
        game.addScore(-ENDING_PRICE);
        boughtEnding = true; // app runs the finale
    }

    private void flash(String msg) {
        flash = msg;
        flashT = 2.6;
    }

    @Override
    public void render(GraphicsContext gc, double w, double h) {
        gc.setFill(Color.web("#0b0814"));
        gc.fillRect(0, 0, w, h);

        atlas.draw(gc, Tile.STORE, w / 2 - 130, 16, 36);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 26));
        gc.setFill(Color.web("#ffd54a"));
        gc.fillText("THE COMPANY STORE", w / 2 - 86, 42);

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#7ee081"));
        String hdr = "score: " + game.score() + "      Floor " + rank + "/" + maxFloors
                + (endingUnlocked() ? "  -  the way out is open" : "");
        gc.setFont(TextFit.fit(hdr, "Segoe UI", FontWeight.BOLD, 16, w - 40));
        gc.fillText(hdr, w / 2, 66);

        double rowH = 56;
        double top = 86;
        Element[] els = Element.values();
        for (int i = 0; i < els.length; i++) {
            row(gc, w, top + i * rowH, rowH, i + 1, statusName(els[i].status),
                    priceFor(els[i].status) + " pts", els[i].tile, game.score() >= priceFor(els[i].status),
                    "#161226");
        }
        double y = top + els.length * rowH;
        // 6: persistent +max life
        row(gc, w, y, rowH, 6, lifeMaxed() ? "VESTED LIFE - MAXED" : "VESTED LIFE  (+1 max life, FOREVER)",
                lifeMaxed() ? "-" : lifePrice() + " pts", Tile.HEART,
                !lifeMaxed() && game.score() >= lifePrice(), "#101a26");
        // 7: persistent haircut - the survival meta that makes the deep floors reachable
        y += rowH;
        boolean canBarber = !trimMaxed() && game.score() >= trimPrice();
        row(gc, w, y, rowH, 7, trimMaxed() ? "THE BARBER - MAXED" : "THE BARBER  (✂ shorter tail as you grow, FOREVER)",
                trimMaxed() ? "-" : trimPrice() + " pts", Tile.SCISSORS, canBarber, "#101a26");
        // 8: descend a floor (the spine)
        y += rowH;
        boolean canDescend = descendAvailable() && game.score() >= floorPrice();
        row(gc, w, y, rowH, 8,
                descendAvailable() ? "THE BACK ROOM  (descend to Floor " + (rank + 1) + "/" + maxFloors + ")"
                        : "THE BACK ROOM - all floors cleared",
                descendAvailable() ? floorPrice() + " pts" : "-", Tile.PORTAL, canDescend, "#101a26");
        // 9: the ending (red herring until the bottom)
        y += rowH;
        boolean canEnd = endingUnlocked() && game.score() >= ENDING_PRICE;
        String endLabel = endingUnlocked() ? "★ BECOME THE FIRM ★  (TRUE ENDING)"
                : !floorsCleared() ? "★ TRUE ENDING ★  (clear all floors first)"
                : "★ TRUE ENDING ★  (read all novels: " + vnDone + "/" + vnTotal + ")";
        row(gc, w, y, rowH, 9, endLabel,
                endingUnlocked() ? ENDING_PRICE + " pts" : "???", Tile.BOSS, canEnd, "#1c1024");

        if (flashT > 0) {
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setFont(TextFit.fit(flash, "Consolas", FontWeight.BOLD, 15, w - 40));
            gc.setFill(Color.web("#ff9ec7"));
            gc.fillText(flash, w / 2, h - 54);
        }
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 14));
        gc.setFill(Color.web("#9fb39f"));
        gc.fillText("press 1-9 to buy   ·   press a direction to slither off", w / 2, h - 28);
    }

    private void row(GraphicsContext gc, double w, double y, double rowH, int num, String label,
                     String price, Tile tile, boolean afford, String baseHex) {
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(Color.web(afford ? baseHex : "#100c18", 0.92));
        gc.fillRoundRect(34, y, w - 68, rowH - 10, 12, 12);
        atlas.draw(gc, tile, 44, y + 4, 36);
        String text = num + ".  " + label;
        gc.setFont(TextFit.fit(text, "Segoe UI", FontWeight.BOLD, 17, w - 200)); // keep clear of the price
        gc.setFill(afford ? Color.web("#eaf4ea") : Color.web("#6c6478"));
        gc.fillText(text, 92, y + 29);
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.setFill(afford ? Color.web("#ffd54a") : Color.web("#7a5a2a"));
        gc.fillText(price, w - 50, y + 29);
    }

    private static String statusName(StatusKind s) {
        return switch (s) {
            case BURN -> "BURN  (torch obstacles)";
            case SLOW -> "SLOW  (stretch time)";
            case GHOST -> "GHOST  (phase through all)";
            case GOLD -> "GOLD  (double score)";
            case MAGNET -> "MAGNET  (pull food in)";
        };
    }
}
