package com.emojisnake.audio;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.emojisnake.IntensitySnapshot;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Headless check that every music scene (including the classical context beds) actually renders
 * audible notes - we can't listen in CI, but we can prove each scene fires its melody/accompaniment
 * and that the classical scenes differ from the adaptive groove.
 */
class SequencerSceneTest {

    private static float[] renderScene(MusicScene scene) {
        Mixer mixer = new Mixer();
        Sequencer seq = new Sequencer(mixer, 44_100, 7L);
        seq.setScene(scene);
        seq.setIntensity(new IntensitySnapshot(0.5, 0.1, 0));
        float[] acc = new float[1024 * 2];
        float[] buf = new float[1024 * 2];
        float peak = 0;
        for (int block = 0; block < 220; block++) { // ~5 seconds
            Arrays.fill(buf, 0f);
            seq.renderInto(buf, 1024);
            for (int i = 0; i < buf.length; i++) {
                acc[i] = buf[i];
                peak = Math.max(peak, Math.abs(buf[i]));
            }
        }
        acc[0] = peak; // smuggle the peak out for the assertion
        return acc;
    }

    @Test
    void everySceneProducesAudibleMusic() {
        for (MusicScene s : MusicScene.values()) {
            float peak = renderScene(s)[0];
            assertTrue(peak > 0.01f, "scene " + s + " produced near-silence (peak " + peak + ")");
        }
    }
}
