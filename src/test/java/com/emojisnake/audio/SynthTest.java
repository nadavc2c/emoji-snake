package com.emojisnake.audio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.emojisnake.IntensitySnapshot;
import org.junit.jupiter.api.Test;

class SynthTest {

    private static final double SR = 44_100;

    private static double rms(float[] buf) {
        double sum = 0;
        for (float v : buf) sum += v * v;
        return Math.sqrt(sum / buf.length);
    }

    @Test
    void noVoicesIsSilent() {
        Mixer m = new Mixer();
        float[] buf = new float[2048 * 2];
        m.renderVoicesInto(buf, 0, 2048, SR);
        assertEquals(0.0, rms(buf), 1e-9, "a mixer with no notes must be dead silent");
    }

    @Test
    void sfxProducesSound() {
        Mixer m = new Mixer();
        float[] buf = new float[2048 * 2];
        m.triggerSfx(Sfx.EAT, SR);
        m.renderVoicesInto(buf, 0, 2048, SR);
        assertTrue(rms(buf) > 1e-4, "triggering an SFX should produce audible samples");
    }

    @Test
    void masterStageKeepsOutputBounded() {
        Mixer m = new Mixer();
        float[] buf = new float[1024 * 2];
        for (int i = 0; i < 24; i++) {
            m.triggerSfx(Sfx.BONUS, SR); // overload the mix on purpose
        }
        m.renderVoicesInto(buf, 0, 1024, SR);
        m.applyMaster(buf, 0, 1024, 1.0); // max corruption = max drive + crush
        for (float v : buf) {
            assertTrue(v >= -1.0001f && v <= 1.0001f, "master output out of range: " + v);
        }
    }

    @Test
    void sequencerPlaysAudibleMusic() {
        assertTrue(rms(renderMusic(1L)) > 1e-4, "the sequencer should generate audible music");
    }

    @Test
    void sequencerIsDeterministicForSameSeed() {
        assertArrayEquals(renderMusic(42L), renderMusic(42L), 0f,
                "same seed + same intensity must render identical audio");
    }

    private float[] renderMusic(long seed) {
        Mixer mixer = new Mixer();
        Sequencer seq = new Sequencer(mixer, SR, seed);
        seq.setIntensity(new IntensitySnapshot(0.9, 0.8, 4)); // high enough to exercise the rng path
        int frames = 8192;
        float[] block = new float[frames * 2];
        seq.renderInto(block, frames);
        mixer.applyMaster(block, 0, frames, 0.8);
        return block;
    }
}
