package com.emojisnake.mode;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;

/**
 * The achievements viewer, driven headlessly (it draws purely with the GraphicsContext, so a null
 * atlas is never needed and {@code render} isn't exercised here). It's a pause-like viewer, not a
 * paged reader: only the close keys dismiss it.
 */
class AchievementsInterludeTest {

    @Test
    void closesOnXEscEnterOrSpace() {
        for (KeyCode k : new KeyCode[] {KeyCode.X, KeyCode.ESCAPE, KeyCode.ENTER, KeyCode.SPACE}) {
            AchievementsInterlude v = new AchievementsInterlude(Set.of("margin_call"), 5, 13, 0);
            assertFalse(v.isDone());
            v.handleKey(k);
            assertTrue(v.isDone(), k + " should close the viewer");
        }
    }

    @Test
    void staysOpenOnUnrelatedKeys() {
        AchievementsInterlude v = new AchievementsInterlude(Set.of(), 0, 12, 0);
        v.handleKey(KeyCode.M);
        v.handleKey(KeyCode.LEFT);
        v.update(0.1);
        assertFalse(v.isDone(), "a viewer isn't a paged reader - unrelated keys don't close it");
    }

    @Test
    void aNullUnlockedSetIsSafe() {
        AchievementsInterlude v = new AchievementsInterlude(null, 0, 12, 0);
        v.update(0.1);
        assertFalse(v.isDone());
    }
}
