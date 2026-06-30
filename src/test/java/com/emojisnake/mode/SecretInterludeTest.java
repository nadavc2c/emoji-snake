package com.emojisnake.mode;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;

/** The secret interlude is paged: each keypress advances a beat, and it only ends after the last. */
class SecretInterludeTest {

    @Test
    void itDoesNotEndOnATimer() {
        SecretInterlude s = new SecretInterlude();
        for (int i = 0; i < 1200; i++) {
            s.update(1.0 / 60); // 20s of animation - must NOT auto-resume
        }
        assertFalse(s.isDone(), "the secret must be paged by the player, not a timer");
    }

    @Test
    void pagingThroughEveryBeatDismissesIt() {
        SecretInterlude s = new SecretInterlude();
        // It has a small number of beats; pressing a key a few times must page off the end.
        for (int i = 0; i < 10 && !s.isDone(); i++) {
            assertFalse(s.isDone(), "still has pages to show at key " + i);
            s.handleKey(KeyCode.SPACE);
        }
        assertTrue(s.isDone(), "paging past the last beat ends it");
    }
}
