package com.emojisnake.fx;

/**
 * Stateless easing curves for game-feel animation (squash-stretch, zoom punch, fades). All take
 * and return a normalized {@code t} in {@code [0, 1]} unless noted.
 */
public final class Easings {

    private Easings() {}

    public static double clamp01(double t) {
        return t < 0 ? 0 : (t > 1 ? 1 : t);
    }

    public static double easeOutQuad(double t) {
        t = clamp01(t);
        return 1 - (1 - t) * (1 - t);
    }

    public static double easeOutCubic(double t) {
        t = clamp01(t);
        double u = 1 - t;
        return 1 - u * u * u;
    }

    public static double easeInQuad(double t) {
        t = clamp01(t);
        return t * t;
    }

    /** Overshoots past 1 then settles - the classic "pop" for squash-stretch. */
    public static double easeOutBack(double t) {
        t = clamp01(t);
        double c1 = 1.70158;
        double c3 = c1 + 1;
        double u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }

    /** Linear interpolation. */
    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
