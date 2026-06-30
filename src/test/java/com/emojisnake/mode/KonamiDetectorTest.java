package com.emojisnake.mode;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;

class KonamiDetectorTest {

    private static final KeyCode[] SEQ = {
        KeyCode.UP, KeyCode.UP, KeyCode.DOWN, KeyCode.DOWN,
        KeyCode.LEFT, KeyCode.RIGHT, KeyCode.LEFT, KeyCode.RIGHT,
        KeyCode.B, KeyCode.A
    };

    @Test
    void firesOnlyOnTheFullSequence() {
        KonamiDetector k = new KonamiDetector();
        for (int i = 0; i < SEQ.length - 1; i++) {
            assertFalse(k.feed(SEQ[i]), "must not fire mid-sequence at step " + i);
        }
        assertTrue(k.feed(SEQ[SEQ.length - 1]), "should fire on the final key");
    }

    @Test
    void wrongKeyResetsTheProgress() {
        KonamiDetector k = new KonamiDetector();
        k.feed(KeyCode.UP);
        k.feed(KeyCode.UP);
        k.feed(KeyCode.Z); // breaks the chain
        // A fresh full sequence is required again.
        for (int i = 0; i < SEQ.length - 1; i++) {
            assertFalse(k.feed(SEQ[i]));
        }
        assertTrue(k.feed(SEQ[SEQ.length - 1]));
    }
}
