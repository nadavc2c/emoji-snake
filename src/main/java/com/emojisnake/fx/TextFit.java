package com.emojisnake.fx;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/**
 * Pixel-accurate text fitting so on-screen text can NEVER crop at the edges. Character-count wrapping
 * was the recurring bug: a proportional font means N characters is not a fixed width. Everything that
 * draws text near a boundary should route through here - either {@link #fit} (shrink a single line to
 * a width) or {@link #wrap} (word-wrap to a width, hard-splitting any word that is itself too long).
 */
public final class TextFit {

    private TextFit() {
    }

    /** Measured pixel width of {@code s} at {@code font}. */
    public static double width(String s, Font font) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        Text t = new Text(s);
        t.setFont(font);
        return t.getLayoutBounds().getWidth();
    }

    /** A font sized so {@code text} fits {@code maxWidth} (never larger than {@code baseSize}). */
    public static Font fit(String text, String family, FontWeight weight, double baseSize, double maxWidth) {
        Font font = Font.font(family, weight, baseSize);
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return font;
        }
        double w = width(text, font);
        if (w <= maxWidth) {
            return font;
        }
        double scaled = Math.max(7, baseSize * maxWidth / w);
        return Font.font(family, weight, scaled);
    }

    /** Word-wrap {@code text} into lines no wider than {@code maxWidth} at {@code font}. */
    public static List<String> wrap(String text, Font font, double maxWidth) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            if (text != null && !text.isEmpty()) {
                out.add(text);
            }
            return out;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (width(candidate, font) <= maxWidth) {
                if (line.length() > 0) {
                    line.append(' ');
                }
                line.append(word);
            } else if (line.length() == 0) {
                out.addAll(hardSplit(word, font, maxWidth)); // single word too wide
            } else {
                out.add(line.toString());
                line.setLength(0);
                if (width(word, font) <= maxWidth) {
                    line.append(word);
                } else {
                    out.addAll(hardSplit(word, font, maxWidth));
                }
            }
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }

    private static List<String> hardSplit(String word, Font font, double maxWidth) {
        List<String> pieces = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (cur.length() > 0 && width(cur.toString() + c, font) > maxWidth) {
                pieces.add(cur.toString());
                cur.setLength(0);
            }
            cur.append(c);
        }
        if (cur.length() > 0) {
            pieces.add(cur.toString());
        }
        return pieces;
    }
}
