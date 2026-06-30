package com.emojisnake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The rare "shed body" gag: the body freezes into lethal terrain, the head pops off and must crawl
 * back to bite its still-living neck to reassemble. Driven deterministically via the {@code
 * forceShed} hook, so these never depend on the rarity roll. (The snake spawns on the centre row,
 * and {@code placeObstacles} keeps the three rows around it clear, so the manoeuvres below have a
 * guaranteed open path.)
 */
class ShedGagTest {

    /** Spawn at (10,10) heading right, then eat straight ahead until the snake is long enough. */
    private GameState longSnake() {
        GameState g = new GameState(20, 20, 5L);
        while (g.length() < 6) {
            Point head = g.snakeBody().get(0);
            g.forceFood(head.step(Direction.RIGHT));
            g.tick();
        }
        return g; // head (13,10), body trailing left along row 10
    }

    @Test
    void shedFreezesTheBodyAndLeavesTheHeadAlone() {
        GameState g = longSnake();
        Point head = g.snakeBody().get(0);     // (13,10)
        g.forceShed();

        assertTrue(g.isDetached(), "shedding should detach the head");
        assertEquals(1, g.length(), "only the head keeps moving");
        assertEquals(head, g.snakeBody().get(0), "the head stays exactly where it was");
        assertEquals(new Point(12, 10), g.reconnectNode(), "the neck becomes the reconnect node");
        assertSame(Tile.BONUS, g.tileAt(12, 10), "the living neck reads as edible");
        assertSame(Tile.GRAVE, g.tileAt(8, 10), "the rest of the body reads as dead terrain");
    }

    @Test
    void bitingTheNeckReassemblesTheSnake() {
        GameState g = longSnake();
        int before = g.length();               // 6
        g.forceShed();                          // head (13,10), neck (12,10)
        g.forceFood(new Point(0, 0));           // park food away so the lone head doesn't grow en route

        // Loop back to the neck (can't U-turn straight onto it): up, left, then down into it.
        g.setDirection(Direction.UP);
        g.tick();                               // (13,9)
        g.setDirection(Direction.LEFT);
        g.tick();                               // (12,9)
        g.setDirection(Direction.DOWN);
        GameState.Event e = g.tick();           // (12,10) == neck -> reassemble

        assertFalse(g.isDetached(), "biting the neck ends the gag");
        assertSame(GameState.Status.RUNNING, g.status());
        assertEquals(new Point(12, 10), g.snakeBody().get(0), "the head lands where the neck was");
        assertEquals(before, g.length(), "the frozen body reattaches to the (un-grown) lone head");
        assertTrue(g.drainNotices().stream().anyMatch(n -> n.kind() == GameState.Notice.Kind.RECONNECT),
                "a RECONNECT notice fires for the reward juice");
        assertSame(GameState.Event.MOVED, e);
    }

    @Test
    void reconnectingTowardTheBodyDoesNotInstantlySelfCrash() {
        GameState g = longSnake();              // head (13,10), body to the left along row 10
        g.forceFood(new Point(0, 0));           // park food clear of the manoeuvre
        g.forceShed();                          // neck (12,10), body extends left

        // Loop around and bite the neck while heading LEFT - i.e. straight toward the body.
        g.setDirection(Direction.UP);
        g.tick();                               // (13,9)
        g.setDirection(Direction.RIGHT);
        g.tick();                               // (14,9)
        g.setDirection(Direction.DOWN);
        g.tick();                               // (14,10)
        g.setDirection(Direction.LEFT);
        g.tick();                               // (13,10), now heading LEFT
        g.tick();                               // (12,10) == neck -> reassemble (heading LEFT, into body)
        assertFalse(g.isDetached(), "should have reconnected");

        GameState.Event e = g.tick();           // the reassembled snake must not eat itself
        assertSame(GameState.Status.RUNNING, g.status(),
                "reconnecting toward the body must not instantly self-crash");
        assertFalse(e == GameState.Event.CRASHED, "the step after reconnect should be safe");
    }

    @Test
    void theLoneHeadEatsAndGrowsIntoItsOwnSnakeWhileDetached() {
        GameState g = longSnake();              // head (13,10), heading right
        g.forceShed();                          // body freezes to the left; head pops off (length 1)
        int scoreBefore = g.score();
        g.forceFood(new Point(14, 10));         // dead ahead of the lone head
        GameState.Event e = g.tick();           // head -> (14,10), eats it

        assertSame(GameState.Event.ATE_FOOD, e, "the detached head still eats food");
        assertTrue(g.score() > scoreBefore, "eating while detached still accrues score");
        assertTrue(g.isDetached(), "eating doesn't end the shed gag");
        assertEquals(2, g.length(), "the lone head grows into its own new snake");
    }

    @Test
    void detachedGrowthIsKeptAndTheFrozenBodyIsAppendedOnReunion() {
        GameState g = longSnake();              // length 6: head (13,10) + 5 body to the left
        int before = g.length();
        g.forceShed();                          // head (13,10) detached; neck (12,10); 5 frozen segments
        // Eat one ahead so the lone snake grows to length 2, then loop back and bite the neck.
        g.forceFood(new Point(14, 10));
        g.tick();                               // (14,10) eat -> lone snake length 2
        g.forceFood(new Point(0, 0));           // park the next food away
        g.setDirection(Direction.UP);
        g.tick();                               // (14,9)
        g.setDirection(Direction.LEFT);
        g.tick();                               // (13,9)
        g.tick();                               // (12,9)
        g.setDirection(Direction.DOWN);
        g.tick();                               // (12,10) == neck -> reassemble

        assertFalse(g.isDetached(), "reconnected");
        // grew by 1 while detached, so the reunion length exceeds the original by that growth.
        assertEquals(before + 1, g.length(), "kept the detached growth and appended the frozen body");
    }

    @Test
    void touchingTheFrozenBodyKillsTheLoneHead() {
        GameState g = longSnake();
        g.forceShed();                          // head (13,10); frozen body along row 10

        g.setDirection(Direction.UP);
        g.tick();                               // (13,9)
        g.setDirection(Direction.LEFT);
        g.tick();                               // (12,9)
        g.tick();                               // (11,9) - keeps heading left
        g.setDirection(Direction.DOWN);
        GameState.Event e = g.tick();           // (11,10) - a frozen body cell (not the neck)

        assertSame(GameState.Event.CRASHED, e, "running into the frozen body is lethal");
        assertSame(GameState.Status.GAME_OVER, g.status());
    }

    @Test
    void revivingAfterCrashKeepsLengthInPlace() {
        // Unrelated-but-adjacent forgiveness check: an in-place revive must not shrink or move you.
        GameState g = longSnake();              // head (13,10), length 6
        g.forceFood(new Point(0, 0));           // park food well clear of the crash
        g.addObstacle(new Point(14, 10));       // a wall of one, dead ahead
        GameState.Event e = g.tick();           // crash into it
        assertSame(GameState.Event.CRASHED, e);

        java.util.List<Point> bodyBefore = g.snakeBody();
        g.revive();

        assertSame(GameState.Status.RUNNING, g.status());
        assertEquals(bodyBefore, g.snakeBody(), "revive keeps the snake exactly where it died");
        assertFalse(g.obstacles().contains(new Point(14, 10)), "the obstacle it hit is broken");
    }
}
