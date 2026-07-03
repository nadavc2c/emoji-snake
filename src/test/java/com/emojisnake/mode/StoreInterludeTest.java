package com.emojisnake.mode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.emojisnake.Direction;
import com.emojisnake.GameState;
import com.emojisnake.StatusKind;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;

/**
 * Logic of the on-board shop against a real headless {@link GameState} (atlas is render-only, so null
 * is fine). Item slots: 1-5 power-ups, 6 VESTED LIFE (persistent +max life), 7 THE BARBER (persistent
 * "haircut"), 8 THE BACK ROOM (descend a floor), 9 the TRUE ENDING (locked until every floor is
 * cleared AND every novel is read - both THIS run; death resets both counts).
 */
class StoreInterludeTest {

    private static final int FLOORS = 5;
    private static final int NOVELS = 5;
    private static final int MAXTRIM = 3;

    /** Shop at a given per-run floor / banked life bonus / per-run novels-read count (no haircut, not yet ended). */
    private static StoreInterlude shop(GameState g, int rank, int lifeBonus, int vnRead) {
        return new StoreInterlude(g, null, 640, 696, rank, FLOORS, lifeBonus, 2, vnRead, NOVELS, 0, MAXTRIM, false);
    }

    /** Shop with a given number of BARBER "haircut" levels already banked. */
    private static StoreInterlude shopWithTrim(GameState g, int trim) {
        return new StoreInterlude(g, null, 640, 696, 0, FLOORS, 0, 2, 0, NOVELS, trim, MAXTRIM, false);
    }

    @Test
    void buyingAnAffordablePowerUpSpendsScoreAndGrantsTheStatus() {
        GameState g = new GameState(20, 20, 5L);
        g.addScore(50);
        int before = g.score();
        shop(g, 0, 0, 0).handleKey(KeyCode.DIGIT3); // GHOST - 15 pts

        assertEquals(before - 15, g.score(), "the price is deducted");
        assertTrue(g.activeStatuses().contains(StatusKind.GHOST), "the bought status is active");
    }

    @Test
    void vestedLifeIsAPersistentPurchaseFlaggedForTheApp() {
        GameState g = new GameState(20, 20, 5L);
        g.addScore(50);
        StoreInterlude shop = shop(g, 0, 0, 0);
        shop.handleKey(KeyCode.DIGIT6); // VESTED LIFE - 40 pts for the first

        assertEquals(50 - 40, g.score(), "the upgrade is paid for");
        assertEquals(1, shop.livesBought(), "the app is told to bank a permanent +max-life");
    }

    @Test
    void vestedLifeIsRefusedOnceMaxed() {
        GameState g = new GameState(20, 20, 5L);
        g.addScore(500);
        StoreInterlude shop = shop(g, 0, 2, 0); // already at the cap (maxLifeBonus 2)
        shop.handleKey(KeyCode.DIGIT6);

        assertEquals(500, g.score(), "a maxed upgrade charges nothing");
        assertEquals(0, shop.livesBought());
    }

    @Test
    void theBarberIsAPersistentPurchaseFlaggedForTheApp() {
        GameState g = new GameState(20, 20, 5L);
        g.addScore(60);
        StoreInterlude shop = shopWithTrim(g, 0);
        shop.handleKey(KeyCode.DIGIT7); // THE BARBER - 40 pts for the first haircut

        assertEquals(60 - 40, g.score(), "the haircut is paid for");
        assertEquals(1, shop.trimBought(), "the app is told to bank a permanent haircut level");
    }

    @Test
    void theBarberIsRefusedOnceMaxed() {
        GameState g = new GameState(20, 20, 5L);
        g.addScore(500);
        StoreInterlude shop = shopWithTrim(g, MAXTRIM); // already at the haircut cap
        shop.handleKey(KeyCode.DIGIT7);

        assertEquals(500, g.score(), "a maxed haircut charges nothing");
        assertEquals(0, shop.trimBought());
    }

    @Test
    void theBarberIsRefusedWhenUnaffordable() {
        GameState g = new GameState(20, 20, 5L);
        g.addScore(10); // first haircut is 40
        StoreInterlude shop = shopWithTrim(g, 0);
        shop.handleKey(KeyCode.DIGIT7);

        assertEquals(10, g.score(), "an unaffordable haircut charges nothing");
        assertEquals(0, shop.trimBought());
    }

    @Test
    void theBackRoomDescendsAFloorAndCloses() {
        GameState g = new GameState(20, 20, 5L);
        g.addScore(50);
        StoreInterlude shop = shop(g, 0, 0, 0); // floor 0 -> price 25
        shop.handleKey(KeyCode.DIGIT8);

        assertEquals(50 - 25, g.score(), "the descent costs the floor price");
        assertTrue(shop.boughtSecret(), "buying it tells the app to descend a floor");
        assertTrue(shop.isDone(), "and closes the shop");
    }

    @Test
    void theTrueEndingIsLockedWhileFloorsRemain() {
        GameState g = new GameState(20, 20, 5L);
        g.addScore(1000);
        StoreInterlude shop = shop(g, 0, 0, NOVELS); // novels read, but floors not cleared
        shop.handleKey(KeyCode.DIGIT9);

        assertEquals(1000, g.score(), "the locked ending charges nothing");
        assertFalse(shop.boughtEnding());
        assertFalse(shop.isDone());
    }

    @Test
    void theTrueEndingIsLockedUntilEveryNovelIsRead() {
        GameState g = new GameState(20, 20, 5L);
        g.addScore(1000);
        StoreInterlude shop = shop(g, FLOORS, 0, NOVELS - 1); // all floors, but one novel short
        shop.handleKey(KeyCode.DIGIT9);

        assertEquals(1000, g.score(), "incomplete novels keep the ending locked");
        assertFalse(shop.boughtEnding(), "you must finish all novels first");
    }

    @Test
    void theTrueEndingIsRealOnceEveryFloorAndNovelIsDone() {
        GameState g = new GameState(20, 20, 5L);
        g.addScore(1000);
        StoreInterlude shop = shop(g, FLOORS, 0, NOVELS); // floors cleared AND novels read
        shop.handleKey(KeyCode.DIGIT9);

        assertTrue(shop.boughtEnding(), "with both prerequisites met, the ending is finally real");
        assertTrue(shop.isDone());
        assertTrue(g.score() < 1000, "and it costs you");
    }

    @Test
    void theEndingCannotBeReboughtOnceYouveAlreadyWon() {
        GameState g = new GameState(20, 20, 5L);
        g.addScore(1000);
        // Prerequisites met AND already ended: the "keep grinding" run must not re-charge for the finale.
        StoreInterlude shop =
                new StoreInterlude(g, null, 640, 696, FLOORS, FLOORS, 0, 2, NOVELS, NOVELS, 0, MAXTRIM, true);
        shop.handleKey(KeyCode.DIGIT9);

        assertFalse(shop.boughtEnding(), "you already run the firm - the ending is not for sale again");
        assertEquals(1000, g.score(), "an already-won ending charges nothing");
    }

    @Test
    void aClosedCheckoutIgnoresFurtherPurchases() {
        // Regression: key-repeat on 8 (or any digit after choosing an exit) used to keep charging
        // in the gap before the app's next frame observed isDone() - double-paying for one descent.
        GameState g = new GameState(20, 20, 5L);
        g.addScore(100);
        StoreInterlude shop = shop(g, 0, 0, 0); // floor 0 -> price 25
        shop.handleKey(KeyCode.DIGIT8);         // buy the descent (closes the shop)
        int after = g.score();

        shop.handleKey(KeyCode.DIGIT8);         // key-repeat lands after isDone()
        shop.handleKey(KeyCode.DIGIT3);         // so does a stray power-up buy

        assertEquals(after, g.score(), "a closed checkout must not charge again");
        assertTrue(shop.boughtSecret(), "the single bought descent still stands");
    }

    @Test
    void aDirectionKeyLeavesTheShopOnThatHeading() {
        GameState g = new GameState(20, 20, 5L);
        StoreInterlude shop = shop(g, 0, 0, 0);
        shop.handleKey(KeyCode.UP);

        assertSame(Direction.UP, shop.exitDirection());
        assertTrue(shop.isDone());
    }
}
