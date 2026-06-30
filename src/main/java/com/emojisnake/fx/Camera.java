package com.emojisnake.fx;

import java.util.Random;
import javafx.scene.Node;

/**
 * Trauma-based screen shake + zoom punch, applied to the render root {@link Node} via its
 * translate/scale transforms. Trauma (0..1) is added on impacts and decays each frame; the
 * actual shake is {@code trauma^2} so small hits are subtle and big ones slam (Vlambeer's
 * approach).
 *
 * <p>At rest the transform is exactly identity (scale 1.0, no offset) so every edge tile is
 * fully visible - no permanent overscan. The only zoom is a transient overscan applied
 * <em>during</em> a shake, sized to cover the shake's own translation, so the board edge never
 * peeks in while shaking.
 */
public final class Camera {

    private static final double MAX_OFFSET = 16;       // px at full trauma
    private static final double SHAKE_OVERSCAN = 0.065; // zoom-in at full shake (covers MAX_OFFSET)
    private static final double TRAUMA_DECAY = 1.6;     // per second
    private static final double PUNCH_DECAY = 5.0;      // per second

    private final Random rng = new Random();

    private double trauma;
    private double zoomPunch;
    private double offsetX, offsetY, scale = 1.0;

    /** Add a shake impulse (clamped). ~0.25 = small hit, ~0.6 = death. */
    public void addTrauma(double amount) {
        trauma = clamp01(trauma + amount);
    }

    /** Add a momentary zoom-in pop. */
    public void kick(double amount) {
        zoomPunch += amount;
    }

    public void update(double dt, double intensity) {
        trauma = Math.max(0, trauma - TRAUMA_DECAY * dt);
        zoomPunch = Math.max(0, zoomPunch - PUNCH_DECAY * dt);

        double shake = trauma * trauma;
        double reach = MAX_OFFSET * (0.45 + 0.55 * intensity);
        offsetX = (rng.nextDouble() * 2 - 1) * shake * reach;
        offsetY = (rng.nextDouble() * 2 - 1) * shake * reach;
        // Identity at rest; zoom in only as much as the current shake/punch needs so the offset
        // never reveals the board edge. No steady overscan, so edge tiles stay whole.
        scale = 1.0 + zoomPunch + shake * SHAKE_OVERSCAN;
    }

    /** Write the current transform onto the render root (scale pivots on its center). */
    public void apply(Node node) {
        node.setTranslateX(offsetX);
        node.setTranslateY(offsetY);
        node.setScaleX(scale);
        node.setScaleY(scale);
    }

    public void reset() {
        trauma = 0;
        zoomPunch = 0;
        offsetX = offsetY = 0;
        scale = 1.0;
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}
