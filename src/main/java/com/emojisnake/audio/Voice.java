package com.emojisnake.audio;

/**
 * A single monophonic oscillator + {@link Envelope}, mixed into a shared stereo float buffer.
 * Voices are pooled by {@link Mixer} and reused (no per-note allocation). Everything runs in
 * {@code [-1, 1]} float; the engine converts to 16-bit PCM at the very end.
 *
 * <p>Supports an optional linear pitch sweep so one voice can produce the classic ascending
 * "blip" / descending "bonk" SFX without any audio files.
 */
final class Voice {

    private Waveform waveform = Waveform.SQUARE;
    private double freq;          // current base frequency (Hz), mutated by sweep
    private double level;         // voice gain 0..1
    private double pan = 0.5;     // 0 = hard left, 1 = hard right
    private double pulseWidth = 0.5;
    private double detuneMul = 1.0;

    private double phase;         // 0..1 oscillator phase
    private int lfsr = 0x7FFF;    // 15-bit noise shift register
    private double lastNoise;

    private boolean sweeping;
    private double sweepTarget;
    private double sweepPerSample;

    private boolean active;
    private final Envelope env = new Envelope();

    /** Trigger a note. {@code sustain == 0} makes a self-terminating percussive pluck. */
    void noteOn(Waveform wf, double freq, double level, double pan, double pulseWidth,
                double attack, double decay, double sustain, double release) {
        this.waveform = wf;
        this.freq = freq;
        this.level = level;
        this.pan = clamp01(pan);
        this.pulseWidth = pulseWidth;
        this.detuneMul = 1.0;
        this.phase = 0;
        this.sweeping = false;
        this.lastNoise = 0;
        env.configure(attack, decay, sustain, release);
        env.trigger();
        active = true;
    }

    /** Multiplicative pitch offset (e.g. 1.01 for a detuned, beating unison). */
    void setDetune(double mul) {
        this.detuneMul = mul;
    }

    /** Glide the pitch linearly to {@code targetHz} over {@code seconds}. */
    void sweepTo(double targetHz, double seconds, double sampleRate) {
        this.sweepTarget = targetHz;
        this.sweepPerSample = (targetHz - freq) / Math.max(1.0, seconds * sampleRate);
        this.sweeping = true;
    }

    void noteOff() {
        env.release();
    }

    boolean isActive() {
        return active;
    }

    /** Mix {@code frames} stereo frames into {@code buf} starting at frame {@code offset}. */
    void mixInto(float[] buf, int offset, int frames, double sampleRate) {
        if (!active) return;
        double lGain = Math.cos(pan * Math.PI / 2) * level;
        double rGain = Math.sin(pan * Math.PI / 2) * level;

        for (int i = 0; i < frames; i++) {
            double e = env.nextSample(sampleRate);
            if (env.isFinished()) {
                active = false;
                break;
            }
            if (sweeping) {
                freq += sweepPerSample;
                if ((sweepPerSample >= 0 && freq >= sweepTarget)
                        || (sweepPerSample < 0 && freq <= sweepTarget)) {
                    freq = sweepTarget;
                    sweeping = false;
                }
            }

            double f = freq * detuneMul;
            double s;
            if (waveform == Waveform.NOISE) {
                phase += f / sampleRate;
                if (phase >= 1.0) {
                    phase -= 1.0;
                    int bit = (lfsr ^ (lfsr >> 1)) & 1;
                    lfsr = (lfsr >> 1) | (bit << 14);
                    lastNoise = ((lfsr & 1) == 0) ? 0.85 : -0.85;
                }
                s = lastNoise;
            } else {
                phase += f / sampleRate;
                if (phase >= 1.0) phase -= 1.0;
                s = osc(phase);
            }

            double v = s * e;
            int idx = (offset + i) * 2;
            buf[idx] += (float) (v * lGain);
            buf[idx + 1] += (float) (v * rGain);
        }
    }

    private double osc(double p) {
        return switch (waveform) {
            case SQUARE -> p < pulseWidth ? 1.0 : -1.0;
            case TRIANGLE -> 4.0 * Math.abs(p - 0.5) - 1.0;
            case SAW -> 2.0 * p - 1.0;
            case NOISE -> lastNoise; // handled in mixInto, unreachable here
        };
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}
