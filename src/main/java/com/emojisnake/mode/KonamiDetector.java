package com.emojisnake.mode;

import javafx.scene.input.KeyCode;

/**
 * Watches the key stream for the Konami code (↑ ↑ ↓ ↓ ← → ← → B A). {@link #feed} returns true
 * exactly once when the full sequence completes - a hidden, never-advertised secret (the
 * Frog-Fractions "make it hard to discover" principle).
 */
public final class KonamiDetector {

    private static final KeyCode[] CODE = {
        KeyCode.UP, KeyCode.UP, KeyCode.DOWN, KeyCode.DOWN,
        KeyCode.LEFT, KeyCode.RIGHT, KeyCode.LEFT, KeyCode.RIGHT,
        KeyCode.B, KeyCode.A
    };

    private int index;

    /** Feed a key; returns true the moment the sequence is completed. */
    public boolean feed(KeyCode code) {
        if (code == CODE[index]) {
            index++;
            if (index == CODE.length) {
                index = 0;
                return true;
            }
        } else {
            // Restart, but allow the wrong key to be the start of a fresh attempt.
            index = (code == CODE[0]) ? 1 : 0;
        }
        return false;
    }
}
