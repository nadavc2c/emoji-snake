package com.emojisnake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IntensityModelTest {

    private static GameState newGame() {
        return new GameState(20, 20, 7L);
    }

    /** Eat the cell straight ahead {@code n} times to grow/level the snake deterministically. */
    private static void eat(GameState g, int n) {
        for (int i = 0; i < n; i++) {
            Point head = g.snakeBody().get(0);
            g.forceFood(head.step(g.direction()));
            g.tick();
        }
    }

    @Test
    void comboCountsConsecutiveEats() {
        IntensityModel m = new IntensityModel();
        m.registerEvent(GameState.Event.ATE_FOOD);
        m.registerEvent(GameState.Event.ATE_FOOD);
        assertEquals(2, m.combo());
    }

    @Test
    void crashSlamsIntensityToMax() {
        IntensityModel m = new IntensityModel();
        m.registerEvent(GameState.Event.CRASHED);
        assertEquals(1.0, m.intensity(), 1e-9);
    }

    @Test
    void intensityStaysInUnitRangeAndComboLiftsIt() {
        IntensityModel m = new IntensityModel();
        GameState g = newGame();
        for (int i = 0; i < 10; i++) {
            m.registerEvent(GameState.Event.ATE_FOOD);
            m.update(g, 0.1);
        }
        double v = m.intensity();
        assertTrue(v >= 0 && v <= 1, "intensity must stay in [0,1], was " + v);
        assertTrue(v > 0, "an active combo should lift intensity above 0");
    }

    @Test
    void corruptionFloorRisesWithLevel() {
        IntensityModel m = new IntensityModel();
        GameState g = newGame();
        eat(g, 6); // 6 foods -> level 2
        assertTrue(g.level() >= 2, "precondition: should have leveled up");
        m.update(g, 0.016);
        assertTrue(m.corruption() > 0, "corruption should follow the level floor");
    }

    @Test
    void resetClearsEverything() {
        IntensityModel m = new IntensityModel();
        m.registerEvent(GameState.Event.CRASHED);
        m.nudgeCorruption(0.5);
        m.reset();
        assertEquals(0, m.intensity(), 1e-9);
        assertEquals(0, m.corruption(), 1e-9);
        assertEquals(0, m.combo());
    }
}
