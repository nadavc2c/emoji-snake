package com.emojisnake.mode;

import com.emojisnake.vn.Choice;
import com.emojisnake.vn.Story;
import com.emojisnake.vn.StoryNode;
import com.emojisnake.vn.StoryState;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * The "slop AI visual novel" interlude: the snake loop is suspended and a short, branching,
 * dark-capitalist lawyer drama takes over the screen - background, character portrait, a typewritered
 * line of dialogue, and choices. A wrong choice routes to a lethal ending; the app reads {@link
 * #died()} in its interlude-finish step and spends a life (or ends the run). Art comes from {@link
 * VnArt} and falls back to drawn placeholder panels when a file is missing.
 *
 * <p>Pacing: text reveals letter-by-letter; a keypress fast-forwards the reveal, then advances. On a
 * junction, ↑/↓ (or number keys) pick a choice and Enter/Space confirms. On an ending, any key
 * dismisses it - so the player always gets to read how it went before control returns.
 */
public final class VisualNovelInterlude implements Interlude {

    private static final double CHARS_PER_SEC = 48;
    private static final int WRAP_CHARS = 50;

    private final StoryState state;
    private final VnArt art;
    private final boolean meta;

    private double elapsed;       // whole-interlude clock (animation)
    private double textT;         // seconds the current node's text has been revealing
    private int selected;         // highlighted choice index
    private boolean acknowledged; // an ending has been dismissed -> we're done

    private String wrappedFor;    // node id the cached wrap belongs to
    private List<String> lines = List.of();
    private int totalChars;

    public VisualNovelInterlude(Story story, VnArt art, boolean meta) {
        this.state = new StoryState(story);
        this.art = art;
        this.meta = meta;
    }

    public Story story() {
        return state.story();
    }

    /** True if the player walked into a lethal ending (the app spends a life). */
    public boolean died() {
        return state.died();
    }

    /** Score the reached (safe) ending pays out - big for a ruthless win, 1 for a meek exit. */
    public int reward() {
        return state.reward();
    }

    /** A short line for the app to toast on a non-lethal outcome. */
    public String outcomeBlurb() {
        return meta ? "you survived “" + state.story().title() + "”. for now."
                : "the end of “" + state.story().title() + "”.";
    }

    @Override
    public void update(double dt) {
        elapsed += dt;
        textT += dt;
    }

    @Override
    public boolean isDone() {
        return acknowledged;
    }

    @Override
    public void handleKey(KeyCode code) {
        ensureWrapped();
        if (!textComplete()) {
            textT = totalChars / CHARS_PER_SEC + 1; // first keypress: reveal the whole line
            return;
        }
        StoryNode cur = state.current();
        if (cur.isEnding()) {
            acknowledged = true; // any key leaves the ending
            return;
        }
        int n = cur.choices().size();
        switch (code) {
            case UP, W -> selected = (selected - 1 + n) % n;
            case DOWN, S -> selected = (selected + 1) % n;
            case ENTER, SPACE, RIGHT, D -> advance();
            case DIGIT1 -> pick(0, n);
            case DIGIT2 -> pick(1, n);
            case DIGIT3 -> pick(2, n);
            case DIGIT4 -> pick(3, n);
            default -> { /* ignore */ }
        }
    }

    private void pick(int i, int n) {
        if (i < n) {
            selected = i;
            advance();
        }
    }

    private void advance() {
        state.choose(selected);
        selected = 0;
        textT = 0;        // restart the typewriter for the new node
        wrappedFor = null; // force a re-wrap of the new node's text
    }

    // --- rendering -----------------------------------------------------------

    @Override
    public void render(GraphicsContext gc, double w, double h) {
        ensureWrapped();
        StoryNode cur = state.current();
        double hue = (elapsed * (meta ? 60 : 25)) % 360; // eye-bleed escalates once the meta is awake

        drawBackground(gc, w, h, cur.background());
        drawScanlines(gc, w, h);
        if (meta) {
            drawGlitchBands(gc, w, h);
        }
        drawPortrait(gc, w, h, cur);
        drawHeader(gc, w, h, hue);
        drawDialogue(gc, w, h, cur);

        if (textComplete()) {
            if (cur.isEnding()) {
                drawEndingPrompt(gc, w, h, cur);
            } else {
                drawChoices(gc, w, h, cur);
            }
        }
        drawNeonBorder(gc, w, h, hue);
    }

    /** CRT scanlines over the scene (kept off the text - drawn before the dialogue box). */
    private void drawScanlines(GraphicsContext gc, double w, double h) {
        gc.setFill(Color.color(0, 0, 0, 0.16));
        for (double y = 0; y < h; y += 3) {
            gc.fillRect(0, y, w, 1);
        }
    }

    /** Thin DIFFERENCE-blended neon bands drifting in the upper area (away from the dialogue). */
    private void drawGlitchBands(GraphicsContext gc, double w, double h) {
        gc.setGlobalBlendMode(BlendMode.DIFFERENCE);
        for (int i = 0; i < 2; i++) {
            double y = (Math.sin(elapsed * (0.7 + i * 0.5) + i * 2.3) * 0.5 + 0.5) * (h * 0.42);
            gc.setGlobalAlpha(0.22);
            gc.setFill(Color.hsb((elapsed * 120 + i * 90) % 360, 1.0, 1.0));
            gc.fillRect(0, y, w, 3.0 + i * 2);
        }
        gc.setGlobalAlpha(1.0);
        gc.setGlobalBlendMode(BlendMode.SRC_OVER);
    }

    /** Dating-sim flourish: the novel's punny title + a pulsing "love meter" (vector hearts). */
    private void drawHeader(GraphicsContext gc, double w, double h, double hue) {
        double pulse = 0.5 + 0.5 * Math.sin(elapsed * 4.0);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        gc.setFill(Color.hsb(hue, 0.5, 1.0));
        gc.fillText(state.story().title(), 18, 28);
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        gc.setFill(Color.web("#ff5a9e"));
        gc.fillText("♥ ♥", w - 40, 28);                 // two full hearts
        gc.setFill(Color.web("#ff5a9e", 0.2 + 0.5 * pulse));      // ...and one fluttering
        gc.fillText("♥", w - 20, 28);
    }

    /** Hue-cycling neon frame with a complementary offset stroke for a cheap chromatic shimmer. */
    private void drawNeonBorder(GraphicsContext gc, double w, double h, double hue) {
        gc.setGlobalBlendMode(BlendMode.SRC_OVER);
        gc.setLineWidth(4);
        gc.setStroke(Color.hsb(hue, 0.85, 1.0, 0.9));
        gc.strokeRect(3, 3, w - 6, h - 6);
        gc.setLineWidth(2);
        gc.setStroke(Color.hsb((hue + 180) % 360, 0.85, 1.0, 0.5));
        gc.strokeRect(6, 6, w - 12, h - 12);
    }

    private void drawBackground(GraphicsContext gc, double w, double h, String bg) {
        Image img = art.background(bg);
        if (img != null) {
            gc.drawImage(img, 0, 0, w, h);
            gc.setFill(Color.color(0, 0, 0, 0.18)); // gentle darkening for text legibility
            gc.fillRect(0, 0, w, h);
            return;
        }
        // Fallback: a moody gradient tinted by which scene it is.
        Color top = sceneTint(bg);
        LinearGradient grad = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, top.darker()), new Stop(1, Color.web("#07060a")));
        gc.setFill(grad);
        gc.fillRect(0, 0, w, h);
    }

    private static Color sceneTint(String bg) {
        if (bg == null) {
            return Color.web("#1a1430");
        }
        return switch (bg) {
            case "office" -> Color.web("#172033");
            case "boardroom" -> Color.web("#241a12");
            case "courtroom" -> Color.web("#1c1726");
            case "skyline" -> Color.web("#0e2333");
            case "basement" -> Color.web("#1f0d12");
            default -> Color.web("#1a1430");
        };
    }

    private void drawPortrait(GraphicsContext gc, double w, double h, StoryNode cur) {
        if (!cur.hasPortrait()) {
            return;
        }
        double boxTop = h - 168;
        double size = Math.min(380, boxTop - 24);
        double slide = Math.min(1, elapsed / 0.3);          // slide up on entry
        double bob = Math.sin(elapsed * 2.0) * 4;           // gentle idle bob
        double px = w / 2 - size / 2;
        double py = boxTop - size + 18 + bob + (1 - slide) * 40;

        Image img = art.portrait(cur.portrait(), cur.expr());
        if (img != null) {
            gc.setGlobalAlpha(slide);
            gc.drawImage(img, px, py, size, size);
            gc.setGlobalAlpha(1.0);
        } else {
            drawPortraitPlaceholder(gc, cur, px, py, size, slide);
        }
    }

    /** A flat tinted card with the character's name - stands in until the slop art is generated. */
    private void drawPortraitPlaceholder(GraphicsContext gc, StoryNode cur,
                                         double px, double py, double size, double slide) {
        gc.setGlobalAlpha(slide);
        gc.setFill(portraitTint(cur.portrait()));
        gc.fillRoundRect(px, py, size, size, 28, 28);
        gc.setStroke(Color.web("#000000", 0.4));
        gc.setLineWidth(3);
        gc.strokeRoundRect(px, py, size, size, 28, 28);

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#ffffff", 0.92));
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 116));
        String initial = (cur.speaker() == null || cur.speaker().isBlank())
                ? "?" : cur.speaker().substring(0, 1).toUpperCase();
        gc.fillText(initial, px + size / 2, py + size / 2 + 40);
        gc.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 16));
        gc.setFill(Color.web("#ffffff", 0.6));
        gc.fillText("[ slop.exe rendering... ]", px + size / 2, py + size - 26);
        gc.setGlobalAlpha(1.0);
    }

    private static Color portraitTint(String portrait) {
        if (portrait == null) {
            return Color.web("#2a2440");
        }
        return switch (portrait) {
            case "partner" -> Color.web("#3a2230");
            case "vance" -> Color.web("#3a1f3c");
            case "client" -> Color.web("#23323a");
            case "intern" -> Color.web("#2a3a23");
            case "glass" -> Color.web("#3a3320");
            default -> Color.web("#2a2440");
        };
    }

    private void drawDialogue(GraphicsContext gc, double w, double h, StoryNode cur) {
        double boxTop = h - 158;
        double boxH = h - boxTop - 14;
        gc.setFill(Color.color(0.02, 0.02, 0.04, 0.82));
        gc.fillRoundRect(14, boxTop, w - 28, boxH, 16, 16);
        gc.setStroke(Color.web("#7ee081", 0.55));
        gc.setLineWidth(2);
        gc.strokeRoundRect(14, boxTop, w - 28, boxH, 16, 16);

        // Speaker tab.
        if (cur.speaker() != null && !cur.speaker().isBlank()) {
            gc.setTextAlign(TextAlignment.LEFT);
            gc.setFont(Font.font("Consolas", FontWeight.BOLD, 18));
            gc.setFill(Color.web("#7ee081"));
            gc.fillText(cur.speaker(), 30, boxTop + 26);
        }

        // Typewritered, wrapped body text.
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 18));
        gc.setFill(Color.web("#eaf4ea"));
        int remaining = typedChars();
        double ty = boxTop + 54;
        for (String ln : lines) {
            if (remaining <= 0) {
                break;
            }
            String shown = ln.length() <= remaining ? ln : ln.substring(0, remaining);
            gc.fillText(shown, 30, ty);
            remaining -= shown.length();
            ty += 24;
        }
    }

    private void drawChoices(GraphicsContext gc, double w, double h, StoryNode cur) {
        List<Choice> choices = cur.choices();
        double rowH = 34;
        double startY = (h - 158) - choices.size() * rowH - 12;
        double pulse = 0.5 + 0.5 * Math.sin(elapsed * 6.0);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(javafx.geometry.VPos.CENTER); // vertically centre the label in its box
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        double boxH = rowH - 6;
        for (int i = 0; i < choices.size(); i++) {
            double y = startY + i * rowH;
            boolean sel = i == selected;
            gc.setFill(sel ? Color.web("#1a2a1a", 0.92) : Color.web("#0c120c", 0.78));
            gc.fillRoundRect(30, y, w - 60, boxH, 10, 10);
            if (sel) {
                gc.setStroke(Color.web("#7ee081", 0.5 + 0.5 * pulse));
                gc.setLineWidth(2);
                gc.strokeRoundRect(30, y, w - 60, boxH, 10, 10);
            }
            gc.setFill(sel ? Color.web("#aef5b0") : Color.web("#9fb39f"));
            gc.fillText((sel ? "▸ " : "  ") + (i + 1) + ". " + choices.get(i).label(), 42, y + boxH / 2);
        }
        gc.setTextBaseline(javafx.geometry.VPos.BASELINE); // restore the default for later text
    }

    private void drawEndingPrompt(GraphicsContext gc, double w, double h, StoryNode cur) {
        if (((int) (elapsed * 2)) % 2 != 0) {
            return; // blink
        }
        String msg;
        if (cur.lethal()) {
            msg = "[ a life ends. press any key. ]";
        } else if (cur.reward() > 0) {
            msg = "[ +" + cur.reward() + " pts. press any key. ]";
        } else {
            msg = "[ press any key. ]";
        }
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 16));
        gc.setFill(cur.lethal() ? Color.web("#ff5a6a") : Color.web("#7ee081"));
        gc.fillText(msg, w / 2, h - 158 - 16);
    }

    // --- text helpers --------------------------------------------------------

    private void ensureWrapped() {
        String id = state.current().id();
        if (id.equals(wrappedFor)) {
            return;
        }
        wrappedFor = id;
        lines = wrap(state.current().text(), WRAP_CHARS);
        totalChars = lines.stream().mapToInt(String::length).sum();
    }

    private int typedChars() {
        return (int) Math.min(totalChars, textT * CHARS_PER_SEC);
    }

    private boolean textComplete() {
        return typedChars() >= totalChars;
    }

    private static List<String> wrap(String text, int maxChars) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() + word.length() + 1 > maxChars && line.length() > 0) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }
}
