package com.emojisnake.audio;

/**
 * A linear ADSR amplitude envelope advanced one sample at a time. Times are in seconds;
 * {@link #nextSample(double)} is called once per output sample with the sample rate, so the
 * envelope is sample-accurate and allocation-free. A voice is "done" once it has been released
 * and the level has fallen back to zero.
 */
final class Envelope {

    private enum Stage { IDLE, ATTACK, DECAY, SUSTAIN, RELEASE }

    private double attack = 0.004;
    private double decay = 0.06;
    private double sustain = 0.6;
    private double release = 0.10;

    private Stage stage = Stage.IDLE;
    private double level;        // current amplitude 0..1
    private double releaseFrom;  // level captured at note-off, so release ramps from there

    void configure(double attack, double decay, double sustain, double release) {
        this.attack = Math.max(1e-4, attack);
        this.decay = Math.max(1e-4, decay);
        this.sustain = clamp01(sustain);
        this.release = Math.max(1e-4, release);
    }

    void trigger() {
        stage = Stage.ATTACK;
        level = 0;
    }

    void release() {
        if (stage != Stage.IDLE) {
            stage = Stage.RELEASE;
            releaseFrom = level;
        }
    }

    /** Advance one sample and return the current amplitude in {@code [0, 1]}. */
    double nextSample(double sampleRate) {
        double dt = 1.0 / sampleRate;
        switch (stage) {
            case ATTACK -> {
                level += dt / attack;
                if (level >= 1.0) {
                    level = 1.0;
                    stage = Stage.DECAY;
                }
            }
            case DECAY -> {
                level -= dt * (1.0 - sustain) / decay;
                if (level <= sustain) {
                    level = sustain;
                    // A zero-sustain envelope is a percussive one-shot - finish instead of
                    // hanging silently in SUSTAIN forever (which would leak the voice).
                    stage = sustain <= 1e-4 ? Stage.IDLE : Stage.SUSTAIN;
                }
            }
            case SUSTAIN -> level = sustain;
            case RELEASE -> {
                level -= dt * releaseFrom / release;
                if (level <= 0) {
                    level = 0;
                    stage = Stage.IDLE;
                }
            }
            case IDLE -> level = 0;
        }
        return level;
    }

    boolean isFinished() {
        return stage == Stage.IDLE;
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}
