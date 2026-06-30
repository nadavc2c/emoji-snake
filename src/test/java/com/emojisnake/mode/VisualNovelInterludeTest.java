package com.emojisnake.mode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.emojisnake.vn.Story;
import com.emojisnake.vn.VisualNovels;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;

/**
 * Drives the visual-novel interlude through whole routes headlessly (VnArt is render-only, so a null
 * art is fine). Confirms the reward economy the user defined: ruthless wins pay big, a meek exit pays
 * 1, and only a super-kind choice is lethal. Uses the "Billable Hearts" novel whose intro is:
 * [1] refuse to bill a dying man (super-kind → lethal), [2] bill ruthlessly → the rival junction.
 */
class VisualNovelInterludeTest {

    private static Story billable() {
        return VisualNovels.all().stream()
                .filter(s -> s.id().equals("billable"))
                .findFirst()
                .orElseThrow();
    }

    private static VisualNovelInterlude vn() {
        return new VisualNovelInterlude(billable(), null, true);
    }

    /** Reveal the current line (1st key fast-forwards the typewriter) then select choice {@code digit}. */
    private static void revealAndPick(VisualNovelInterlude vn, KeyCode digit) {
        vn.handleKey(KeyCode.SPACE);
        vn.handleKey(digit);
    }

    @Test
    void theSuperKindChoiceIsLethalAndPaysNothing() {
        VisualNovelInterlude vn = vn();
        revealAndPick(vn, KeyCode.DIGIT1); // "I can't bill a dying man!" -> lethal

        assertTrue(vn.died(), "refusing to be cruel is the BAD END");
        assertEquals(0, vn.reward(), "a death pays no score");
    }

    @Test
    void theRuthlessRouteSurvivesAndPaysBig() {
        VisualNovelInterlude vn = vn();
        revealAndPick(vn, KeyCode.DIGIT2); // bill the corpse -> impress (rival junction)
        revealAndPick(vn, KeyCode.DIGIT1); // bury the rival -> ruthless win

        assertFalse(vn.died(), "ruthlessness is rewarded, not punished");
        assertTrue(vn.reward() >= 20, "a route win pays a big score, was " + vn.reward());
    }

    @Test
    void theMeekRouteSurvivesButPaysOnlyOne() {
        VisualNovelInterlude vn = vn();
        revealAndPick(vn, KeyCode.DIGIT2); // bill ruthlessly -> impress
        revealAndPick(vn, KeyCode.DIGIT2); // "split it fairly" -> meek exit

        assertFalse(vn.died());
        assertEquals(1, vn.reward(), "a meek/neutral exit barely pays");
    }

    @Test
    void anEndingIsNotDoneUntilDismissed() {
        VisualNovelInterlude vn = vn();
        revealAndPick(vn, KeyCode.DIGIT1); // walk into the lethal ending
        assertFalse(vn.isDone(), "the player must get to read the ending");

        vn.handleKey(KeyCode.SPACE); // reveal the ending line
        vn.handleKey(KeyCode.SPACE); // acknowledge
        assertTrue(vn.isDone());
    }
}
