package com.emojisnake.audio;

/**
 * A fixed pool of {@link Voice}s plus the master output stage. Pure DSP - no audio device, no
 * threads - so it is deterministic and unit-testable: feed it note/sfx triggers, render into a
 * float buffer, and assert on the samples.
 *
 * <p>The master stage turns "corruption" into audible hostility: drive into a {@code tanh}
 * saturator, then a bit-crush quantizer at high corruption. Both are bounded, so the output
 * stays in {@code [-1, 1]}.
 */
public final class Mixer {

    private static final int POLYPHONY = 16;
    private static final double MASTER_GAIN = 0.55;

    private final Voice[] voices = new Voice[POLYPHONY];
    private int cursor;

    public Mixer() {
        for (int i = 0; i < voices.length; i++) {
            voices[i] = new Voice();
        }
    }

    /** Grab a free voice (or steal the next in round-robin) and start a note on it. */
    Voice triggerNote(Waveform wf, double freq, double level, double pan, double pulseWidth,
                      double attack, double decay, double sustain, double release) {
        Voice v = claimVoice();
        v.noteOn(wf, freq, level, pan, pulseWidth, attack, decay, sustain, release);
        return v;
    }

    /** Fire a one-shot SFX as a pitch-swept voice. */
    public void triggerSfx(Sfx sfx, double sampleRate) {
        Voice v = claimVoice();
        v.noteOn(sfx.waveform, sfx.startHz, sfx.level, 0.5, 0.5,
                0.003, sfx.seconds, 0.0, 0.02);
        v.sweepTo(sfx.endHz, sfx.seconds, sampleRate);
    }

    private Voice claimVoice() {
        for (Voice v : voices) {
            if (!v.isActive()) return v;
        }
        // All busy - steal round-robin so we don't always kill the same note.
        Voice v = voices[cursor];
        cursor = (cursor + 1) % voices.length;
        return v;
    }

    /** Sum all active voices into {@code buf} (stereo interleaved). Caller clears the buffer. */
    public void renderVoicesInto(float[] buf, int frameOffset, int frames, double sampleRate) {
        for (Voice v : voices) {
            v.mixInto(buf, frameOffset, frames, sampleRate);
        }
    }

    /** Master gain + corruption-driven saturation + bit-crush, in place. */
    public void applyMaster(float[] buf, int frameOffset, int frames, double corruption) {
        double drive = 1.0 + corruption * 5.0;
        boolean crush = corruption > 0.4;
        // 16-bit clean down to ~5-bit at full corruption.
        int levels = (int) Math.round(lerp(65536, 32, (corruption - 0.4) / 0.6));
        double step = 2.0 / Math.max(2, levels);

        int start = frameOffset * 2;
        int end = (frameOffset + frames) * 2;
        for (int i = start; i < end; i++) {
            double x = Math.tanh(buf[i] * MASTER_GAIN * drive);
            if (crush) {
                x = Math.round(x / step) * step;
            }
            buf[i] = (float) x;
        }
    }

    int activeVoiceCount() {
        int n = 0;
        for (Voice v : voices) {
            if (v.isActive()) n++;
        }
        return n;
    }

    private static double lerp(double a, double b, double t) {
        t = t < 0 ? 0 : (t > 1 ? 1 : t);
        return a + (b - a) * t;
    }
}
