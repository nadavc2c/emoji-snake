package com.emojisnake;

/**
 * An immutable, thread-safe snapshot of the game's current "madness" level. Produced on the
 * JavaFX thread by {@link IntensityModel} and handed across to the audio daemon thread via an
 * {@code AtomicReference}, so it must stay a plain value object (no references to mutable game
 * state).
 *
 * <p>Two independent axes drive every crazy subsystem:
 * <ul>
 *   <li>{@code intensity} - fast, short-term excitement (snake speed + recent eating combo).
 *       Eases up and down. Drives tempo, bloom, shake budget, particle counts.</li>
 *   <li>{@code corruption} - slow, long-term decay of the "cute" surface into the hostile
 *       eye-bleed aesthetic. Ratchets up on level-ups / milestones and decays gently. Drives
 *       hue-cycling speed, audio detune/distortion, and (later phases) the glitch illusions.</li>
 * </ul>
 * Both are clamped to {@code [0, 1]}. {@code combo} is the raw current eat-combo count, exposed
 * for effects that want a discrete kick rather than the smoothed intensity.
 */
public record IntensitySnapshot(double intensity, double corruption, int combo) {

    public static final IntensitySnapshot CALM = new IntensitySnapshot(0, 0, 0);

    /** Combined "heat" used by effects that don't care which axis drove it. */
    public double heat() {
        return Math.min(1.0, intensity * 0.6 + corruption * 0.6);
    }

    /** A dialled-down copy for "calm mode" - keeps the game readable for sensitive players. */
    public IntensitySnapshot tamed() {
        return new IntensitySnapshot(intensity * 0.5, Math.min(corruption, 0.22), combo);
    }
}
