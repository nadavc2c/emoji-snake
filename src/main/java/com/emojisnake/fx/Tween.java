package com.emojisnake.fx;

import java.util.function.DoubleUnaryOperator;

/**
 * A tiny one-shot, time-based tween: drives a value from {@code from} to {@code to} over a
 * duration, shaped by an {@link Easings} curve. Used for short juice animations such as the
 * squash-stretch "pop" on an eaten tile. Re-armable via {@link #restart()}.
 */
public final class Tween {

    private final double from;
    private final double to;
    private final double duration;
    private final DoubleUnaryOperator easing;
    private double elapsed;

    public Tween(double from, double to, double duration, DoubleUnaryOperator easing) {
        this.from = from;
        this.to = to;
        this.duration = Math.max(1e-4, duration);
        this.easing = easing;
        this.elapsed = duration; // start "done"; call restart() to fire
    }

    public void restart() {
        elapsed = 0;
    }

    public void update(double dt) {
        if (elapsed < duration) {
            elapsed += dt;
        }
    }

    public boolean isDone() {
        return elapsed >= duration;
    }

    public double value() {
        double t = Easings.clamp01(elapsed / duration);
        return Easings.lerp(from, to, easing.applyAsDouble(t));
    }
}
