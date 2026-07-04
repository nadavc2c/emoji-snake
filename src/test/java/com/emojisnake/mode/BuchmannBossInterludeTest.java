package com.emojisnake.mode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;

/**
 * The Buchmann (Pac-Man) boss, driven headlessly. The atlas is render-only (ghosts), so a null atlas
 * is fine as long as {@code render} isn't called. Covers the two results the app reads
 * ({@code hits()} / {@code cleared()}), the harness seams, and that the maze sim runs to its cap.
 */
class BuchmannBossInterludeTest {

    private static BuchmannBossInterlude boss(int lives) {
        return new BuchmannBossInterlude(null, 640, 696, lives);
    }

    @Test
    void forceClearedGraduatesAndEnds() {
        BuchmannBossInterlude b = boss(3);
        assertFalse(b.isDone());
        b.forceCleared();
        assertTrue(b.isDone());
        assertTrue(b.cleared());
        assertEquals(0, b.hits());
    }

    @Test
    void forceHitsEndsWithoutClearing() {
        BuchmannBossInterlude b = boss(3);
        b.forceHits(2);
        assertTrue(b.isDone());
        assertFalse(b.cleared());
        assertEquals(2, b.hits());
    }

    @Test
    void hitsClampToTheLivesBroughtIn() {
        BuchmannBossInterlude b = boss(2);
        b.forceHits(99);
        assertEquals(2, b.hits(), "you can't be caught more times than you had lives");
    }

    @Test
    void theFightEndsByItsDurationCapEvenIfNeverCleared() {
        // 99 lives + never steering: i-frames cap the catches well under 99, so it can only end on
        // the ~24s duration cap. Exercises the ghost AI + collision path end to end.
        BuchmannBossInterlude b = boss(99);
        for (int i = 0; i < 25 * 60 && !b.isDone(); i++) {
            b.update(1.0 / 60);
        }
        assertTrue(b.isDone(), "the fight must end by its duration cap even if never cleared");
    }

    @Test
    void steeringDrivesTheSimWithoutError() {
        BuchmannBossInterlude b = boss(99);
        KeyCode[] dirs = {KeyCode.LEFT, KeyCode.UP, KeyCode.RIGHT, KeyCode.DOWN};
        for (int i = 0; i < 600; i++) {
            b.handleKey(dirs[i % dirs.length]);
            b.update(1.0 / 60);
        }
        assertTrue(b.hits() >= 0, "the maze/ghost sim must run to completion without throwing");
    }
}
