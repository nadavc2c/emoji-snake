package com.emojisnake.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.paint.Color;

/**
 * A capped pool of {@link Particle}s rendered additively (so overlapping bursts bloom into
 * white-hot cores) - the impact spray on eat / death / bonus. The cap protects frame time at
 * high intensity (silent drop once full; the visual loss is imperceptible).
 */
public final class ParticleSystem {

    private static final int MAX_PARTICLES = 600;

    private final List<Particle> particles = new ArrayList<>();
    private final Random rng = new Random();

    /** Spray {@code count} particles outward from a point in a color family. */
    public void burst(double x, double y, int count, Color base, double speed) {
        for (int i = 0; i < count; i++) {
            if (particles.size() >= MAX_PARTICLES) return;
            double angle = rng.nextDouble() * Math.PI * 2;
            double v = speed * (0.3 + rng.nextDouble());
            Particle p = new Particle();
            p.x = x;
            p.y = y;
            p.vx = Math.cos(angle) * v;
            p.vy = Math.sin(angle) * v;
            p.gravity = 220;
            p.life = 0.35 + rng.nextDouble() * 0.5;
            p.size = 3 + rng.nextDouble() * 4;
            p.color = base.deriveColor(rng.nextDouble() * 40 - 20, 1, 1, 1); // hue jitter
            particles.add(p);
        }
    }

    public void update(double dt) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.update(dt);
            if (!p.alive()) {
                particles.remove(i);
            }
        }
    }

    public void render(GraphicsContext gc) {
        if (particles.isEmpty()) return;
        BlendMode prev = gc.getGlobalBlendMode();
        gc.setGlobalBlendMode(BlendMode.ADD);
        for (Particle p : particles) {
            double f = p.fade();
            gc.setGlobalAlpha(f);
            gc.setFill(p.color);
            double s = p.size * (0.5 + f);
            gc.fillOval(p.x - s / 2, p.y - s / 2, s, s);
        }
        gc.setGlobalAlpha(1.0);
        gc.setGlobalBlendMode(prev);
    }

    public int count() {
        return particles.size();
    }

    public void clear() {
        particles.clear();
    }
}
