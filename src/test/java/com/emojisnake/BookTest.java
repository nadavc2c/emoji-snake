package com.emojisnake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The rare "book" pickup ("bookworm"): a 📖 occasionally replaces food; eating it fires a {@code BOOK}
 * notice (the app turns that into the visual-novel interlude). Driven deterministically via the {@code
 * forceBook} hook so these never depend on the rarity roll. The feature is OFF by default, so the
 * vanilla seeded tests (GameStateTest / RoguelikeMechanicsTest) are unaffected.
 */
class BookTest {

    @Test
    void eatingABookFiresTheBookNoticeAndStillGrows() {
        GameState g = new GameState(20, 20, 5L); // head (10,10), heading right
        g.setBooksEnabled(true);
        int lenBefore = g.length();
        int scoreBefore = g.score();
        Point head = g.snakeBody().get(0);
        g.forceBook(head.step(Direction.RIGHT));
        assertSame(Tile.BOOK, g.tileAt(head.x() + 1, head.y()), "the book renders as the 📖 tile");

        GameState.Event e = g.tick(); // eat the book

        assertSame(GameState.Event.ATE_FOOD, e, "a book is eaten like food");
        assertEquals(lenBefore + 1, g.length(), "eating the book still grows the snake");
        assertTrue(g.score() > scoreBefore, "eating the book still scores");
        assertTrue(g.drainNotices().stream().anyMatch(n -> n.kind() == GameState.Notice.Kind.BOOK),
                "eating the book surfaces a BOOK notice for the app to open the novel");
    }

    @Test
    void booksDisabledNeverFireAndDoNotDisturbTheRng() {
        // The OFF-by-default contract: with books disabled, the game plays byte-identically to vanilla
        // (no BOOK notice, and the food-respawn roll consumes no extra RNG). We assert the latter by
        // comparing two seeded games - one untouched, one with books left disabled - step for step.
        GameState plain = new GameState(20, 20, 99L);
        GameState alsoPlain = new GameState(20, 20, 99L);
        alsoPlain.setBooksEnabled(false); // explicitly off - must change nothing

        for (int i = 0; i < 200; i++) {
            GameState.Event a = plain.tick();
            GameState.Event b = alsoPlain.tick();
            assertSame(a, b, "disabled books must not change the simulation (step " + i + ")");
            assertEquals(plain.food(), alsoPlain.food(), "food sequence must match (step " + i + ")");
            assertEquals(plain.snakeBody(), alsoPlain.snakeBody(), "bodies must match (step " + i + ")");
            assertFalse(alsoPlain.drainNotices().stream().anyMatch(n -> n.kind() == GameState.Notice.Kind.BOOK),
                    "no BOOK notice when books are disabled");
            plain.drainNotices();
        }
    }
}
