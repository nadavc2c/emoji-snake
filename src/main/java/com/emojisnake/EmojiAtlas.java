package com.emojisnake;

import java.util.EnumMap;
import java.util.Map;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;

/**
 * Loads the bundled OpenMoji color PNGs once and draws them onto a {@link GraphicsContext}.
 * Using images (rather than emoji font glyphs) guarantees full-color tiles on every
 * platform - Java desktop toolkits otherwise often fall back to monochrome emoji outlines.
 */
public final class EmojiAtlas {

    private final Map<Tile, Image> images = new EnumMap<>(Tile.class);

    public EmojiAtlas() {
        for (Tile tile : Tile.values()) {
            var url = EmojiAtlas.class.getResource(tile.resourcePath());
            if (url == null) {
                throw new IllegalStateException("Missing bundled emoji asset: " + tile.resourcePath());
            }
            // Decode to 72px (native OpenMoji size), smooth=true for crisp downscaling.
            images.put(tile, new Image(url.toExternalForm(), 72, 72, true, true));
        }
    }

    public void draw(GraphicsContext gc, Tile tile, double x, double y, double size) {
        gc.drawImage(images.get(tile), x, y, size, size);
    }
}
