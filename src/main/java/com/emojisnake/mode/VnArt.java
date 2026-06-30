package com.emojisnake.mode;

import java.util.HashMap;
import java.util.Map;
import javafx.scene.image.Image;

/**
 * Lenient loader for the (AI-generated, "slop") visual-novel art bundled under {@code /vn/}. Unlike
 * {@link com.emojisnake.EmojiAtlas}, a missing file is <em>not</em> fatal - it returns {@code null}
 * so the interlude can fall back to a drawn placeholder panel. That keeps the game runnable while the
 * art set is still being generated, and means one missing portrait never crashes a run.
 *
 * <p>Naming convention the art pipeline targets:
 * {@code /vn/characters/<portrait>_<expr>.png} and {@code /vn/bg/<background>.png}.
 */
public final class VnArt {

    private final Map<String, Image> cache = new HashMap<>();

    private Image load(String path) {
        if (cache.containsKey(path)) {
            return cache.get(path);
        }
        Image img = null;
        var url = VnArt.class.getResource(path);
        if (url != null) {
            try {
                Image candidate = new Image(url.toExternalForm());
                if (!candidate.isError()) {
                    img = candidate;
                }
            } catch (RuntimeException ignored) {
                // treat any decode failure as "no art" - the interlude draws a placeholder
            }
        }
        cache.put(path, img);
        return img;
    }

    /** The character portrait for {@code portrait} in {@code expr}, falling back to its neutral pose. */
    public Image portrait(String portrait, String expr) {
        if (portrait == null || portrait.isBlank()) {
            return null;
        }
        Image img = load("/vn/characters/" + portrait + "_" + expr + ".png");
        if (img == null) {
            img = load("/vn/characters/" + portrait + "_neutral.png");
        }
        return img;
    }

    public Image background(String background) {
        if (background == null || background.isBlank()) {
            return null;
        }
        return load("/vn/bg/" + background + ".png");
    }

    public Image title() {
        return load("/vn/title.png");
    }
}
