package com.emojisnake.audio;

import com.emojisnake.IntensitySnapshot;
import java.util.Random;

/**
 * The adaptive chiptune sequencer. Owns musical timing and drives the {@link Mixer}'s voices. In the
 * default {@link MusicScene#NORMAL} scene it plays a dark phrygian/minor groove that reacts in real
 * time to the latest {@link IntensitySnapshot} (intensity → tempo + layering; corruption → detune,
 * thinner pulses, tritone stabs, on top of the {@link Mixer}'s master distortion).
 *
 * <p>The other scenes are <b>context interludes</b>: each plays a recognizable public-domain
 * classical melody as note data through the same synth (so still no audio files) - Dies Irae for the
 * boss, Für Elise in the shop, Pachelbel's Canon for the visual novel, an eerie whole-tone descent
 * for the secret, and Ode to Joy for the CEO ending.
 *
 * <p>Pure timing/DSP (no audio device): {@link #renderInto} fills a buffer and fires steps at
 * sample-accurate boundaries, so it is deterministic given a seed and unit-testable. The NORMAL path
 * is unchanged from before, so the existing synth tests still hold.
 */
public final class Sequencer {

    private static final int STEPS_PER_BEAT = 4;     // 16th notes
    private static final double BPM_MIN = 92;
    private static final double BPM_MAX = 232;
    private static final int TONIC = 45;             // A2-ish root for the bass

    // A warmer, moving minor loop (i - VI - III - VII in a low register) - more musical than the old
    // static {0,0,5,7} that made the groove feel simple/annoying.
    private static final int[] PROGRESSION = {0, -4, 3, -2};
    // A short, singable motif (scale degrees above the bar root) so the lead carries a melody instead
    // of a bare triad arpeggio.
    private static final int[] MELODY = {0, 3, 7, 3, 5, 7, 10, 7, 5, 3, 0, 3, 7, 12, 10, 7};

    // --- Classical scores: {midi, durationSteps} pairs; midi = -1 is a rest. ---------------------
    private static final int[][] DIES_IRAE = { // boss: doom
        {65, 2}, {64, 2}, {65, 2}, {62, 4}, {64, 2}, {60, 2}, {62, 4},
        {65, 2}, {64, 2}, {65, 2}, {62, 4}, {67, 2}, {65, 2}, {64, 2}, {62, 6}, {-1, 2},
    };
    private static final int[][] FUR_ELISE = { // shop: classy
        {76, 2}, {75, 2}, {76, 2}, {75, 2}, {76, 2}, {71, 2}, {74, 2}, {72, 2}, {69, 4},
        {-1, 2}, {60, 2}, {64, 2}, {69, 2}, {71, 4},
        {-1, 2}, {64, 2}, {68, 2}, {71, 2}, {72, 4}, {-1, 4},
    };
    private static final int[][] CANON = { // vn: romantic, flowing (Pachelbel chord arpeggios)
        {62, 2}, {66, 2}, {69, 2}, {74, 2}, {61, 2}, {64, 2}, {69, 2}, {73, 2},
        {59, 2}, {62, 2}, {66, 2}, {71, 2}, {58, 2}, {61, 2}, {66, 2}, {70, 2},
        {59, 2}, {62, 2}, {67, 2}, {71, 2}, {57, 2}, {62, 2}, {66, 2}, {69, 2},
        {59, 2}, {62, 2}, {67, 2}, {71, 2}, {61, 2}, {64, 2}, {69, 2}, {73, 2},
    };
    private static final int[][] WHOLE_TONE = { // secret: eerie descent
        {72, 8}, {70, 8}, {68, 8}, {66, 8}, {64, 8}, {62, 8}, {60, 8}, {-1, 8},
    };
    private static final int[][] ODE_TO_JOY = { // ending: triumphant
        {76, 4}, {76, 4}, {77, 4}, {79, 4}, {79, 4}, {77, 4}, {76, 4}, {74, 4},
        {72, 4}, {72, 4}, {74, 4}, {76, 4}, {76, 6}, {74, 2}, {74, 8},
        {76, 4}, {76, 4}, {77, 4}, {79, 4}, {79, 4}, {77, 4}, {76, 4}, {74, 4},
        {72, 4}, {72, 4}, {74, 4}, {76, 4}, {74, 6}, {72, 2}, {72, 8},
    };

    private final Mixer mixer;
    private final double sampleRate;
    private final Random rng;

    private long step;
    private int samplesIntoStep;
    private int samplesPerStep;

    private double intensity;
    private double corruption;

    private MusicScene scene = MusicScene.NORMAL;
    private long melodyIdx;      // cursor into the current scene's melody
    private int melodyStepsLeft; // steps remaining on the current melody note

    public Sequencer(Mixer mixer, double sampleRate, long seed) {
        this.mixer = mixer;
        this.sampleRate = sampleRate;
        this.rng = new Random(seed);
        this.samplesPerStep = computeSamplesPerStep();
    }

    /** Update the live musical parameters (called once per audio block). */
    public void setIntensity(IntensitySnapshot snap) {
        this.intensity = snap.intensity();
        this.corruption = snap.corruption();
    }

    /** Switch the musical bed; restarts the melody cursor on a real change. */
    public void setScene(MusicScene s) {
        if (s != null && s != scene) {
            scene = s;
            melodyIdx = 0;
            melodyStepsLeft = 0;
        }
    }

    /**
     * Render {@code frames} stereo frames, firing sequencer steps at their exact sample
     * boundaries (so tempo changes don't smear timing). The caller has already zeroed the
     * buffer and applies the master stage afterwards.
     */
    public void renderInto(float[] buf, int frames) {
        int done = 0;
        while (done < frames) {
            int toStepEnd = samplesPerStep - samplesIntoStep;
            int chunk = Math.min(frames - done, toStepEnd);
            mixer.renderVoicesInto(buf, done, chunk, sampleRate);
            done += chunk;
            samplesIntoStep += chunk;
            if (samplesIntoStep >= samplesPerStep) {
                samplesIntoStep = 0;
                fireStep();
                step++;
                samplesPerStep = computeSamplesPerStep();
            }
        }
    }

    private int computeSamplesPerStep() {
        double bpm = switch (scene) {
            case NORMAL -> BPM_MIN + (BPM_MAX - BPM_MIN) * clamp01(intensity);
            case BOSS -> 150;
            case SHOP -> 80;
            case VN -> 68;
            case SECRET -> 58;
            case ENDING -> 104;
            case CRASH -> 120; // the error drone just needs a steady clock to retrigger on
        };
        return (int) Math.round(sampleRate * 60.0 / (bpm * STEPS_PER_BEAT));
    }

    private void fireStep() {
        if (scene == MusicScene.NORMAL) {
            fireAdaptiveStep();
        } else if (scene == MusicScene.CRASH) {
            fireCrashStep();
        } else {
            fireScoreStep();
        }
    }

    /**
     * The adaptive chiptune groove - reworked warmer + more melodic. A rounded triangle bass on the
     * moving minor progression, a soft octave pad, a singable triangle melody (the {@link #MELODY}
     * motif) instead of a bare arpeggio, and - crucially - NO ear-splitting 3-8kHz noise hiss (the old
     * source of the "annoying"). Corruption now colours it with a quiet low tritone drone, not screech.
     */
    private void fireAdaptiveStep() {
        int posInBar = (int) (step % 16);
        long bar = step / 16;
        int root = TONIC + PROGRESSION[(int) (bar % PROGRESSION.length)];

        double stepSeconds = (double) samplesPerStep / sampleRate;

        // Warm sustained bass + a quiet octave pad for body, on every beat.
        if (posInBar % STEPS_PER_BEAT == 0) {
            mixer.triggerNote(Waveform.TRIANGLE, noteHz(root), 0.50, 0.5, 0.5,
                    0.006, stepSeconds * 3.6, 0.0, 0.09);
            mixer.triggerNote(Waveform.TRIANGLE, noteHz(root + 12), 0.15, 0.5, 0.5,
                    0.02, stepSeconds * 3.6, 0.0, 0.14);
        }

        // A rounder kick on the downbeats (triangle sweep, not a saw stab).
        if (posInBar == 0 || posInBar == 8) {
            Voice kick = mixer.triggerNote(Waveform.TRIANGLE, 120, 0.55, 0.5, 0.5,
                    0.002, 0.10, 0.0, 0.02);
            kick.sweepTo(46, 0.09, sampleRate);
        }

        // Warm melodic lead: the motif over the chord, on a soft triangle; it phrases faster as the
        // game speeds up. Corruption only lightly detunes it (unease), never turns it to noise.
        if (intensity > 0.15) {
            int gap = Math.max(2, (int) Math.round(4 * (1 - intensity)));
            if (step % gap == 0) {
                int deg = MELODY[(int) ((step / gap) % MELODY.length)];
                int leadMidi = root + 12 + deg;
                double warm = 0.55 - corruption * 0.12;
                double pan = (step % 2 == 0) ? 0.42 : 0.58;
                Voice lead = mixer.triggerNote(Waveform.TRIANGLE, noteHz(leadMidi),
                        0.26, pan, warm, 0.006, stepSeconds * 1.6, 0.0, 0.07);
                lead.setDetune(1.0 + corruption * 0.02);
            }
        }

        // Corruption adds dread with a low, quiet tritone drone once per bar - not an 8kHz hiss.
        if (corruption > 0.5 && posInBar == 0) {
            mixer.triggerNote(Waveform.TRIANGLE, noteHz(root + 6), 0.12 * corruption, 0.5, 0.5,
                    0.05, stepSeconds * 6, 0.0, 0.3);
        }

        // A single soft, low-level closed hat for groove at higher intensity - gentle, not shrill.
        if (intensity > 0.5 && posInBar % 4 == 2) {
            mixer.triggerNote(Waveform.NOISE, 2200, 0.06, 0.5, 0.5,
                    0.001, 0.04, 0.0, 0.01);
        }
    }

    /** The fake-crash "music": no melody at all - a harsh, glitchy system-error drone + static. */
    private void fireCrashStep() {
        double stepSeconds = (double) samplesPerStep / sampleRate;
        // Constant static crackle.
        mixer.triggerNote(Waveform.NOISE, 1500, 0.20, 0.5, 0.5,
                0.001, stepSeconds * 0.8, 0.0, 0.01);
        // A low, detuned "dying machine" buzz on the beat.
        if (step % 4 == 0) {
            Voice buzz = mixer.triggerNote(Waveform.SAW, 72, 0.34, 0.5, 0.5,
                    0.002, stepSeconds * 3.6, 0.0, 0.05);
            buzz.setDetune(1.03);
        }
        // An occasional harsh error "beep".
        if (step % 8 == 3) {
            mixer.triggerNote(Waveform.SQUARE, 210, 0.26, 0.5, 0.5,
                    0.001, 0.12, 0.0, 0.02);
        }
    }

    /** A context scene: play its classical melody + a fitting accompaniment. */
    private void fireScoreStep() {
        int[][] melody = melodyFor(scene);
        double stepSeconds = (double) samplesPerStep / sampleRate;

        if (melodyStepsLeft <= 0) {
            int[] note = melody[(int) (melodyIdx % melody.length)];
            melodyIdx++;
            melodyStepsLeft = Math.max(1, note[1]);
            if (note[0] >= 0) {
                boolean bright = scene == MusicScene.BOSS || scene == MusicScene.ENDING;
                Waveform wave = bright ? Waveform.SQUARE : Waveform.TRIANGLE;
                double level = (scene == MusicScene.BOSS || scene == MusicScene.ENDING) ? 0.32 : 0.24;
                double decay = stepSeconds * note[1] * 0.95;
                double pulse = 0.5 - corruption * 0.18;
                Voice lead = mixer.triggerNote(wave, noteHz(note[0]), level, 0.5, pulse,
                        0.006, decay, 0.0, 0.04);
                if (scene == MusicScene.SECRET || scene == MusicScene.BOSS) {
                    lead.setDetune(1.0 + corruption * 0.04); // a little unease
                }
            }
        }
        melodyStepsLeft--;

        int posInBar = (int) (step % 16);
        int rootMidi = sceneRoot(scene);
        if (scene == MusicScene.BOSS || scene == MusicScene.ENDING) {
            if (posInBar == 0 || posInBar == 8) {
                Voice kick = mixer.triggerNote(Waveform.SAW, 120, 0.5, 0.5, 0.5,
                        0.002, 0.09, 0.0, 0.02);
                kick.sweepTo(48, 0.08, sampleRate);
            }
            if (scene == MusicScene.BOSS && (posInBar == 4 || posInBar == 12)) {
                mixer.triggerNote(Waveform.NOISE, 3200, 0.26, 0.5, 0.5, 0.001, 0.11, 0.0, 0.02);
            }
            if (posInBar % STEPS_PER_BEAT == 0) {
                mixer.triggerNote(Waveform.TRIANGLE, noteHz(rootMidi), 0.40, 0.5, 0.5,
                        0.004, stepSeconds * 3.5, 0.0, 0.05);
            }
        } else if (posInBar == 0) {
            // calm scenes: one soft, long pad note per bar - no drums.
            mixer.triggerNote(Waveform.TRIANGLE, noteHz(rootMidi), 0.30, 0.5, 0.5,
                    0.02, stepSeconds * 14, 0.0, 0.25);
        }
    }

    private static int[][] melodyFor(MusicScene s) {
        return switch (s) {
            case BOSS -> DIES_IRAE;
            case SHOP -> FUR_ELISE;
            case VN -> CANON;
            case SECRET -> WHOLE_TONE;
            case ENDING -> ODE_TO_JOY;
            case NORMAL, CRASH -> ODE_TO_JOY; // unused (NORMAL + CRASH take their own paths)
        };
    }

    private static int sceneRoot(MusicScene s) {
        return switch (s) {
            case BOSS -> 38;   // D2  (Dies Irae, D minor)
            case SHOP -> 45;   // A2  (Für Elise, A minor)
            case VN -> 38;     // D2  (Canon in D)
            case SECRET -> 36; // C2  (whole-tone on C)
            case ENDING -> 48; // C3  (Ode to Joy, C major)
            case NORMAL, CRASH -> TONIC; // unused (handled by their own step paths)
        };
    }

    private static double noteHz(double midi) {
        return 440.0 * Math.pow(2.0, (midi - 69.0) / 12.0);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}
