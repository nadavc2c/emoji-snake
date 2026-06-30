package com.emojisnake.mode;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.emojisnake.Direction;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;

/**
 * Logic of the slot-machine interlude, driven headlessly (atlas is render-only, so a null atlas is
 * fine as long as we never call {@code render}). Covers the "wait for the player" rule the user asked
 * for: the reels must finish AND the player must press a key before it resumes.
 */
class SlotInterludeTest {

    private static final double REVEAL = 2.9; // just past the internal REVEAL constant

    @Test
    void payoutIsBetweenOneAndSix() {
        for (int i = 0; i < 50; i++) {
            int a = new SlotInterlude(null).amount();
            assertTrue(a >= 1 && a <= 6, "payout out of range: " + a);
        }
    }

    @Test
    void doesNotResumeUntilAKeyPressAfterTheReelsStop() {
        SlotInterlude slot = new SlotInterlude(null);
        // Spin well past any natural timeout - it must NOT auto-resume.
        for (int i = 0; i < 600; i++) {
            slot.update(1.0 / 60);
        }
        assertFalse(slot.isDone(), "the slot must wait for the player, not auto-resume on a timer");

        slot.handleKey(KeyCode.SPACE);
        assertTrue(slot.isDone(), "a keypress after the reveal dismisses it");
    }

    @Test
    void aKeyBeforeTheRevealIsIgnored() {
        SlotInterlude slot = new SlotInterlude(null);
        slot.update(0.5); // reels still spinning
        slot.handleKey(KeyCode.SPACE);
        assertFalse(slot.isDone(), "you can't skip the spin");
    }

    @Test
    void aDirectionKeyDismissesAndIsCapturedAsTheExitHeading() {
        SlotInterlude slot = new SlotInterlude(null);
        slot.update(REVEAL);
        slot.handleKey(KeyCode.UP);
        assertTrue(slot.isDone());
        assertSame(Direction.UP, slot.exitDirection(), "the dismiss direction becomes the resume heading");
    }

    @Test
    void aNonDirectionDismissLeavesTheHeadingUnchanged() {
        SlotInterlude slot = new SlotInterlude(null);
        slot.update(REVEAL);
        slot.handleKey(KeyCode.SPACE);
        assertNull(slot.exitDirection(), "Space dismisses but does not steer");
    }
}
