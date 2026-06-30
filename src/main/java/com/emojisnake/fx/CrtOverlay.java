package com.emojisnake.fx;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

/**
 * A pre-rendered CRT look - horizontal scanlines + a darkening vignette baked once into a
 * {@link WritableImage}, then blitted each frame with {@link BlendMode#MULTIPLY}. Drawing alpha
 * scales how strong the effect reads, so the corruption level can fade it in for the hostile
 * "old broken monitor" vibe without rebuilding the image.
 */
public final class CrtOverlay {

    private final WritableImage image;
    private final double w;
    private final double h;

    public CrtOverlay(int width, int height) {
        this.w = width;
        this.h = height;
        this.image = new WritableImage(width, height);
        PixelWriter pw = image.getPixelWriter();

        double cx = width / 2.0;
        double cy = height / 2.0;
        for (int y = 0; y < height; y++) {
            double scan = (y % 2 == 0) ? 1.0 : 0.70; // darken every other row
            for (int x = 0; x < width; x++) {
                double dx = (x - cx) / cx;
                double dy = (y - cy) / cy;
                double d = Math.sqrt(dx * dx + dy * dy);
                double vignette = 1.0 - 0.55 * Math.pow(clamp01(d / 1.4), 2);
                double v = scan * vignette;
                pw.setColor(x, y, Color.color(v, v, v, 1.0));
            }
        }
    }

    public void draw(GraphicsContext gc, double alpha) {
        BlendMode prev = gc.getGlobalBlendMode();
        gc.setGlobalBlendMode(BlendMode.MULTIPLY);
        gc.setGlobalAlpha(clamp01(alpha));
        gc.drawImage(image, 0, 0, w, h);
        gc.setGlobalAlpha(1.0);
        gc.setGlobalBlendMode(prev);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}
