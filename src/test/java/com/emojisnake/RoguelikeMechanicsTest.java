package com.emojisnake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoguelikeMechanicsTest {

    private GameState mechGame() {
        GameState g = new GameState(20, 20, 5L);
        g.setMechanicsEnabled(true);
        return g;
    }

    @Test
    void eatingPowerUpGrantsItsStatus() {
        GameState g = mechGame();
        Point head = g.snakeBody().get(0);
        g.forcePowerUp(head.step(Direction.RIGHT), Element.GHOST);
        g.tick();
        assertTrue(g.hasStatus(StatusKind.GHOST), "ghost power-up should grant the GHOST status");
        assertTrue(g.drainNotices().stream().anyMatch(n -> n.kind() == GameState.Notice.Kind.POWERUP));
    }

    @Test
    void threeOfAKindTriggersSynergy() {
        GameState g = mechGame();
        List<GameState.Notice> all = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Point head = g.snakeBody().get(0);
            g.forcePowerUp(head.step(Direction.RIGHT), Element.FIRE);
            g.tick();
            all.addAll(g.drainNotices());
        }
        assertTrue(all.stream().anyMatch(
                n -> n.kind() == GameState.Notice.Kind.SYNERGY && n.element() == Element.FIRE),
                "eating three FIRE power-ups should fire a synergy");
    }

    @Test
    void ghostPhasesThroughAnObstacle() {
        GameState g = mechGame();
        Point ahead = g.snakeBody().get(0).step(Direction.RIGHT);
        g.addObstacle(ahead);
        g.grantStatus(StatusKind.GHOST, 10);
        g.tick();
        assertSame(GameState.Status.RUNNING, g.status(), "ghost should phase through, not die");
    }

    @Test
    void burnDestroysAnObstacle() {
        GameState g = mechGame();
        Point ahead = g.snakeBody().get(0).step(Direction.RIGHT);
        g.addObstacle(ahead);
        g.grantStatus(StatusKind.BURN, 10);
        g.tick();
        assertSame(GameState.Status.RUNNING, g.status());
        assertFalse(g.obstacles().contains(ahead), "burn should destroy the obstacle");
    }

    @Test
    void slowStretchesTheStepDelay() {
        GameState g = mechGame();
        double normal = g.delayMillis();
        g.grantStatus(StatusKind.SLOW, 10);
        assertTrue(g.delayMillis() > normal, "SLOW should lengthen the step delay");
    }

    @Test
    void portalTeleportsTheHead() {
        GameState g = mechGame();
        Point head = g.snakeBody().get(0);     // (10,10), heading right
        Point portalA = head.step(Direction.RIGHT);
        Point portalB = new Point(5, 10);       // on the clear centre row
        g.forcePortals(portalA, portalB);
        g.tick();
        assertEquals(portalB, g.snakeBody().get(0), "entering a portal teleports the head to its pair");
    }

    @Test
    void ghostPhasingThroughTheBodyKeepsCollisionCellsConsistent() {
        // Regression: while GHOST lets the head phase onto its own body, snakeCells (the O(1)
        // collision set) must never desync from the actual snake - every occupied cell stays
        // registered, so a phased-over segment can't silently become a free cell.
        GameState g = new GameState(20, 20, 3L);
        g.setMechanicsEnabled(true);
        g.clearObstacles();
        g.forceFood(new Point(0, 0)); // park food far from the manoeuvre
        for (int x = 11; x <= 14; x++) {
            g.forceFood(new Point(x, 10));
            g.tick(); // grow to length 7 along the centre row
        }
        g.grantStatus(StatusKind.GHOST, 500);

        Direction[] loop = {Direction.DOWN, Direction.LEFT, Direction.LEFT, Direction.UP, Direction.RIGHT};
        for (int i = 0; i < 30; i++) {
            g.setDirection(loop[i % loop.length]);
            g.tick(); // drives a drifting loop so the head repeatedly crosses its own body
            assertSame(GameState.Status.RUNNING, g.status(), "ghost should keep it alive");
            for (Point p : new java.util.HashSet<>(g.snakeBody())) {
                Tile t = g.tileAt(p.x(), p.y());
                assertTrue(t == Tile.HEAD || t == Tile.BODY,
                        "every snake cell must stay registered (step " + i + ", cell " + p + ")");
            }
        }
    }

    @Test
    void magnetPullsFoodTowardTheHead() {
        GameState g = mechGame();
        g.grantStatus(StatusKind.MAGNET, 10);
        g.forceFood(new Point(15, 10));
        g.tick(); // head -> (11,10); food drifts one step closer
        assertEquals(new Point(14, 10), g.food(), "magnet should pull food one cell toward the head");
    }

    /** Eat five food straight down the clear centre row, returning the final snake length. */
    private static int eatFiveStraight(GameState g) {
        g.clearObstacles();
        for (int x = 11; x <= 15; x++) { // five food -> one level-up (level 2)
            g.forceFood(new Point(x, 10));
            g.tick();
        }
        return g.length();
    }

    @Test
    void theBarberHaircutTrimsTheTailOnLevelUp() {
        // Control: no haircut -> classic +1 per food.
        GameState plain = new GameState(20, 20, 5L);
        int start = plain.length();
        assertEquals(start + 5, eatFiveStraight(plain), "without the barber the snake grows +1 per food");

        // Barbered: same five food, but the level-up sheds trimPerLevel tail segments.
        GameState cut = new GameState(20, 20, 5L);
        cut.setTrimPerLevel(2);
        int trimmed = eatFiveStraight(cut);
        assertEquals(start + 5 - 2, trimmed, "the barber sheds 2 tail segments on the level-up");
        assertTrue(trimmed < plain.length(), "the haircut keeps the snake shorter");
    }

    @Test
    void theBarberNeverTrimsBelowTheFloorLength() {
        GameState g = new GameState(20, 20, 5L);
        g.setTrimPerLevel(9); // an absurdly aggressive haircut
        assertTrue(eatFiveStraight(g) >= 5,
                "the barber must never trim below the minimum length (was " + g.length() + ")");
    }

    @Test
    void trimIsOffByDefaultSoClassicGrowthIsUnchanged() {
        GameState g = new GameState(20, 20, 5L);
        int start = g.length();
        assertEquals(start + 5, eatFiveStraight(g), "with no barber the snake grows classically");
    }
}
