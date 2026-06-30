package com.emojisnake.fx;

import javafx.scene.paint.Color;

/**
 * A single short-lived particle (pos + velocity + gravity + fade). Mutable and pooled-friendly,
 * drawn additively by {@link ParticleSystem} for neon impact bursts.
 */
final class Particle {

    double x, y, vx, vy;
    double age, life;
    double size;
    double gravity;
    Color color;

    boolean alive() {
        return age < life;
    }

    void update(double dt) {
        vy += gravity * dt;
        x += vx * dt;
        y += vy * dt;
        age += dt;
    }

    /** Remaining life in {@code [0, 1]}, for fading out. */
    double fade() {
        return Math.max(0, 1 - age / life);
    }
}
