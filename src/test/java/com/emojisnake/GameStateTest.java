package com.emojisnake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GameStateTest {

    private GameState newGame() {
        return new GameState(20, 20, 1234L); // deterministic seed
    }

    @Test
    void movesOneCellPerTickInChosenDirection() {
        GameState g = newGame();
        Point head = g.snakeBody().get(0);
        g.setDirection(Direction.DOWN); // perpendicular -> allowed
        g.tick();
        Point moved = g.snakeBody().get(0);
        assertEquals(head.x(), moved.x());
        assertEquals(head.y() + 1, moved.y());
    }

    @Test
    void ignores180DegreeReversal() {
        GameState g = newGame(); // moving RIGHT
        Point head = g.snakeBody().get(0);
        g.setDirection(Direction.LEFT); // illegal reverse -> ignored
        g.tick();
        Point moved = g.snakeBody().get(0);
        assertEquals(head.x() + 1, moved.x(), "should keep moving right, not reverse");
    }

    @Test
    void queuesTwoQuickTurnsAcrossTicks() {
        GameState g = newGame(); // moving RIGHT
        // Two fast taps before any tick: a U-turn RIGHT -> UP -> LEFT. The second tap used to be
        // checked against RIGHT and dropped as a reversal; now both should buffer and apply.
        g.setDirection(Direction.UP);
        g.setDirection(Direction.LEFT);

        g.tick();
        assertSame(Direction.UP, g.direction(), "first buffered turn applies this tick");
        g.tick();
        assertSame(Direction.LEFT, g.direction(), "second buffered turn applies the next tick");
    }

    @Test
    void eatingFoodGrowsAndScores() {
        GameState g = newGame();
        int len = g.length();
        Point head = g.snakeBody().get(0);
        g.forceFood(head.step(Direction.RIGHT));

        GameState.Event event = g.tick();

        assertSame(GameState.Event.ATE_FOOD, event);
        assertEquals(len + 1, g.length());
        assertEquals(1, g.score());
    }

    @Test
    void newFoodNeverLandsOnSnakeOrObstacle() {
        GameState g = newGame();
        for (int i = 0; i < 3; i++) {
            Point head = g.snakeBody().get(0);
            g.forceFood(head.step(Direction.RIGHT));
            g.tick();
            Point food = g.food();
            assertFalse(g.snakeBody().contains(food), "food must not spawn on the snake");
            assertFalse(g.obstacles().contains(food), "food must not spawn on an obstacle");
        }
    }

    @Test
    void speedIncreasesAsSnakeEats() {
        GameState g = newGame();
        double before = g.delayMillis();
        Point head = g.snakeBody().get(0);
        g.forceFood(head.step(Direction.RIGHT));
        g.tick();
        assertTrue(g.delayMillis() < before, "delay should shrink after eating");
    }

    @Test
    void pauseFreezesTheSnake() {
        GameState g = newGame();
        g.togglePause();
        assertSame(GameState.Status.PAUSED, g.status());
        Point head = g.snakeBody().get(0);
        g.tick();
        assertEquals(head, g.snakeBody().get(0), "snake must not move while paused");
    }

    @Test
    void drivingIntoTheWallEndsTheGame() {
        GameState g = newGame();
        g.setDirection(Direction.UP);
        GameState.Event last = GameState.Event.MOVED;
        for (int i = 0; i < 25 && g.status() == GameState.Status.RUNNING; i++) {
            last = g.tick();
        }
        assertSame(GameState.Status.GAME_OVER, g.status());
        assertSame(GameState.Event.CRASHED, last);
    }

    @Test
    void reviveKeepsScoreAndStaysInPlace() {
        GameState g = newGame();
        Point head = g.snakeBody().get(0);
        g.forceFood(head.step(Direction.RIGHT));
        g.tick(); // score 1, grown to length 4
        g.setDirection(Direction.UP);
        for (int i = 0; i < 25 && g.status() == GameState.Status.RUNNING; i++) {
            g.tick(); // crash into the top wall (or an obstacle on the way)
        }
        assertSame(GameState.Status.GAME_OVER, g.status());

        int scoreBefore = g.score();
        java.util.List<Point> bodyBefore = g.snakeBody();
        g.revive();
        assertSame(GameState.Status.RUNNING, g.status(), "revive resumes the run");
        assertEquals(scoreBefore, g.score(), "revive keeps the score");
        assertEquals(bodyBefore, g.snakeBody(),
                "revive keeps the snake exactly where it died - same body, same length");
    }

    @Test
    void earlyWallsWrapInsteadOfKilling() {
        GameState g = newGame();
        g.setWrapUntilLevel(9); // wrap at every early level
        for (int i = 0; i < 12; i++) {
            g.tick(); // default heading RIGHT along the clear centre row; crosses the edge
        }
        assertSame(GameState.Status.RUNNING, g.status(), "wrapping walls must not kill");
        Point head = g.snakeBody().get(0);
        assertEquals((10 + 12) % 20, head.x(), "head should have wrapped around the right edge");
        assertEquals(10, head.y());
    }

    @Test
    void speedEasesBackWhenNotEating() {
        GameState g = newGame();
        g.setWrapUntilLevel(9); // survive without walls killing us
        Point head = g.snakeBody().get(0);
        g.forceFood(head.step(Direction.RIGHT));
        g.tick(); // eat once -> momentum up -> faster
        double fast = g.delayMillis();

        g.forceFood(null); // no more food on the board
        for (int i = 0; i < 30; i++) {
            g.tick(); // wrap around, eating nothing -> momentum decays
        }
        assertTrue(g.delayMillis() > fast, "pace should drift back toward calm when not eating");
    }

    @Test
    void restartRebuildsAFreshGame() {
        GameState g = newGame();
        g.setDirection(Direction.UP);
        while (g.status() == GameState.Status.RUNNING) {
            g.tick();
        }
        assertNotEquals(GameState.Status.RUNNING, g.status());
        g.reset();
        assertSame(GameState.Status.RUNNING, g.status());
        assertEquals(0, g.score());
        assertEquals(3, g.length());
    }
}
