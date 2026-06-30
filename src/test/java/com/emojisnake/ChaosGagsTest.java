package com.emojisnake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The chaos/roguelite gags added on top of classic Snake - all OFF by default (so the vanilla seeded
 * tests are untouched) and driven here via deterministic {@code forceX} hooks rather than the rarity
 * rolls. The snake spawns at (10,10) heading RIGHT with its row kept obstacle-free by placeObstacles.
 */
class ChaosGagsTest {

    @Test
    void fleeingFoodBoltsAwayWhenTheHeadIsRightNextToIt() {
        GameState g = new GameState(20, 20, 5L);
        g.setFleeingFoodEnabled(true);
        g.forceFood(new Point(11, 10)); // dead ahead, adjacent
        g.forceFoodFlees(2);

        GameState.Event e = g.tick();

        assertEquals(new Point(12, 10), g.food(), "the food bolts one cell directly away");
        assertNotEquals(GameState.Event.ATE_FOOD, e, "it dodged - not eaten this tick");
        assertTrue(g.drainNotices().stream().anyMatch(n -> n.kind() == GameState.Notice.Kind.FLEE),
                "a FLEE notice fires");
    }

    @Test
    void fleeingFoodGivesUpOnceItsFleesAreSpent() {
        GameState g = new GameState(20, 20, 5L);
        g.setFleeingFoodEnabled(true);
        g.forceFood(new Point(11, 10));
        g.forceFoodFlees(0); // out of flees - it sits there and gets caught

        GameState.Event e = g.tick();

        assertSame(GameState.Event.ATE_FOOD, e, "with no flees left, it is simply eaten");
    }

    @Test
    void bumpingTheStoreOpensItWithoutMovingOntoIt() {
        GameState g = new GameState(20, 20, 5L);
        g.forceStore(new Point(11, 10)); // solid shop dead ahead

        GameState.Event e = g.tick();

        assertSame(GameState.Event.MOVED, e);
        assertEquals(new Point(10, 10), g.snakeBody().get(0), "the head does not move onto the shop");
        assertTrue(g.drainNotices().stream().anyMatch(n -> n.kind() == GameState.Notice.Kind.STORE),
                "bumping the store surfaces a STORE notice for the app to open the shop");
    }

    @Test
    void basiliskTurnsTheHeadToAChickenAndFoodToSnakesUntilTheEggIsEaten() {
        GameState g = new GameState(20, 20, 5L);
        g.forceBasilisk(new Point(11, 10)); // egg dead ahead

        assertTrue(g.isBasilisk());
        assertSame(Tile.CHICKEN, g.tileAt(10, 10), "the head reads as a chicken (the basilisk)");
        assertSame(Tile.EGG, g.tileAt(11, 10), "the cure egg is on the board");
        Point food = g.food();
        if (!food.equals(g.egg()) && !food.equals(g.snakeBody().get(0))) {
            assertSame(Tile.HEAD, g.tileAt(food.x(), food.y()), "food reads as a snake while basilisk");
        }

        GameState.Event e = g.tick(); // head -> (11,10) == egg -> cured

        assertFalse(g.isBasilisk(), "eating the egg reverts the basilisk");
        assertSame(GameState.Event.ATE_BONUS, e);
        assertSame(Tile.HEAD, g.tileAt(11, 10), "the head is a snake again");
        assertTrue(g.drainNotices().stream().anyMatch(n -> n.kind() == GameState.Notice.Kind.CURE));
    }

    @Test
    void slotFoodGamblesInsteadOfGrowingAndPaysOutOverTime() {
        GameState g = new GameState(20, 20, 5L);
        int lenBefore = g.length();
        g.forceSlotFood(new Point(11, 10)); // 🎰 dead ahead

        GameState.Event e = g.tick(); // eat the slot
        assertSame(GameState.Event.ATE_FOOD, e);
        assertSame(lenBefore, g.length(), "a slot does not grow you on eat - it's a gamble");
        assertTrue(g.drainNotices().stream().anyMatch(n -> n.kind() == GameState.Notice.Kind.SLOT),
                "eating the slot fires a SLOT notice (the app spins the reels)");

        // The SlotInterlude would award the winnings; simulate a +3 payout grown over the next ticks.
        g.forceFood(new Point(0, 0)); // park the next food far away
        int len = g.length();
        g.addPendingGrowth(3);
        g.tick();
        g.tick();
        g.tick();
        assertSame(len + 3, g.length(), "the payout grows the snake by 3 over three ticks");
    }

    @Test
    void magnetNeverParksFoodOnTheStore() {
        // Regression: pullFoodTowardHead must use the full free-cell test, or it can drag food onto the
        // solid store cell where tick()'s early-return makes it permanently uneatable (a soft-lock).
        GameState g = new GameState(20, 20, 5L);   // head (10,10) heading RIGHT
        g.grantStatus(StatusKind.MAGNET, 50);
        g.forceFood(new Point(13, 10));
        g.forceStore(new Point(12, 10));            // sits between food and head, one step toward head

        g.tick(); // head -> (11,10); magnet would pull food (13,10) -> (12,10) == store, must be refused

        assertEquals(new Point(13, 10), g.food(), "the magnet must not pull food onto the store");
    }

    @Test
    void detachedHeadDoesNotLeakTheSlotFlagOntoTheRespawn() {
        // Regression: tickDetached must clear foodIsSlot on a respawn, or the next ordinary food
        // inherits the gamble flag and mis-fires the slot interlude (and won't grow).
        GameState g = new GameState(20, 20, 5L);
        g.setGambleEnabled(true);
        g.forceShed();                              // head detached at (10,10)
        g.forceSlotFood(new Point(11, 10));         // a 🎰 dead ahead
        assertTrue(g.isSlotFood());

        g.tick();                                   // lone head eats it; respawn must be a plain food

        assertFalse(g.isSlotFood(), "the respawned food must not inherit the slot flag");
    }

    @Test
    void allGagsStayOffByDefault() {
        // The OFF-by-default contract for the new gags: a plain seeded game plays byte-identically to
        // one with every new gag left disabled (no extra RNG, no new entities).
        GameState plain = new GameState(20, 20, 77L);
        GameState same = new GameState(20, 20, 77L);
        same.setFleeingFoodEnabled(false);
        same.setStoreEnabled(false);
        same.setBasiliskEnabled(false);
        for (int i = 0; i < 200; i++) {
            assertSame(plain.tick(), same.tick(), "step " + i);
            assertEquals(plain.food(), same.food(), "food " + i);
            assertEquals(plain.snakeBody(), same.snakeBody(), "body " + i);
        }
    }
}
