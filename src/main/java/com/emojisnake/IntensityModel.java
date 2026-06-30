package com.emojisnake;

/**
 * The spine of the "crazy" layer. Tracks two eased scalars that every other subsystem
 * (visuals, audio, and - later - the glitch/meta director) subscribes to via an immutable
 * {@link IntensitySnapshot}. Lives on the JavaFX thread; the audio thread only ever sees a
 * snapshot handed across an {@code AtomicReference}.
 *
 * <ul>
 *   <li>{@code intensity} - fast excitement from snake speed + eating combo, eased so it
 *       swells and relaxes smoothly.</li>
 *   <li>{@code corruption} - the slow drain from cute toward hostile eye-bleed. A floor that
 *       rises with the game level, plus transient bumps (bonus gems) that decay back to it.</li>
 * </ul>
 *
 * <p>Phase 1 feeds this from the existing {@link GameState.Event} stream and per-frame state;
 * later phases will route the richer {@code drainEvents()} queue through {@link #registerEvent}.
 */
public final class IntensityModel {

    // Easing rates, expressed per second so they're frame-rate independent.
    private static final double INTENSITY_RISE = 4.0;   // swell quickly toward the target...
    private static final double INTENSITY_FALL = 1.3;   // ...but relax gently
    private static final double CORRUPTION_DECAY = 0.05; // transient corruption bleeds off slowly
    private static final double COMBO_WINDOW_SEC = 2.5;  // eat again within this to keep the combo

    private static final double CORRUPTION_PER_LEVEL = 0.09; // floor climb per level
    private static final double CORRUPTION_BONUS_BUMP = 0.07; // transient kick from a bonus gem

    private double intensity;
    private double corruption;
    private int combo;
    private double sinceLastEat;

    /** Fold a single tick outcome into the model (combo, transient corruption, death slam). */
    public void registerEvent(GameState.Event event) {
        switch (event) {
            case ATE_FOOD -> { combo++; sinceLastEat = 0; }
            case ATE_BONUS -> {
                combo += 2;
                sinceLastEat = 0;
                corruption = clamp01(corruption + CORRUPTION_BONUS_BUMP);
            }
            case CRASHED -> intensity = 1.0; // slam to max on death - pure juice
            case MOVED -> { /* nothing notable */ }
        }
    }

    /** Advance the eased scalars by {@code dt} seconds, reading current speed/level from the game. */
    public void update(GameState game, double dt) {
        if (dt <= 0) return;

        sinceLastEat += dt;
        if (sinceLastEat > COMBO_WINDOW_SEC) {
            combo = 0;
        }

        // Target intensity: mostly raw speed, boosted by an active combo.
        double comboBoost = Math.min(0.45, combo * 0.06);
        double target = clamp01(game.speedFraction() * 0.7 + comboBoost);
        double rate = (target > intensity ? INTENSITY_RISE : INTENSITY_FALL) * dt;
        intensity = clamp01(intensity + (target - intensity) * Math.min(1.0, rate));

        // Corruption: a level-driven floor it never drops below, with transient bumps decaying to it.
        double floor = clamp01((game.level() - 1) * CORRUPTION_PER_LEVEL);
        if (corruption < floor) {
            corruption = floor;
        } else {
            corruption = Math.max(floor, corruption - CORRUPTION_DECAY * dt);
        }
    }

    /** Wipe back to the calm starting state (called on game restart). */
    public void reset() {
        intensity = 0;
        corruption = 0;
        combo = 0;
        sinceLastEat = 0;
    }

    public IntensitySnapshot snapshot() {
        return new IntensitySnapshot(intensity, corruption, combo);
    }

    public double intensity()  { return intensity; }
    public double corruption() { return corruption; }
    public int combo()         { return combo; }

    /** Manual corruption nudge for the {@code --debug} keys (and tests). */
    public void nudgeCorruption(double delta) {
        corruption = clamp01(corruption + delta);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}
