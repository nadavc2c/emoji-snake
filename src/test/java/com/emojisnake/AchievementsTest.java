package com.emojisnake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The pure achievement catalog + the 📈 stock-reward formula (ceiling + crash-proof floor). */
class AchievementsTest {

    @Test
    void everyIdIsUniqueAndFullyPopulated() {
        Set<String> ids = new HashSet<>();
        for (Achievements.Achievement a : Achievements.ALL) {
            assertTrue(ids.add(a.id()), "duplicate achievement id: " + a.id());
            assertNotNull(a.title(), a.id() + " needs a title");
            assertNotNull(a.hint(), a.id() + " needs a hint");
        }
        assertEquals(ids.size(), Achievements.count());
    }

    @Test
    void ceilingRisesOnePerUnlockFromTheBase() {
        assertEquals(Achievements.BASE_MAX_SHARES, Achievements.capFor(0));
        assertEquals(Achievements.BASE_MAX_SHARES + 1, Achievements.capFor(1));
        assertEquals(Achievements.BASE_MAX_SHARES + Achievements.count(),
                Achievements.capFor(Achievements.count()));
        assertEquals(Achievements.BASE_MAX_SHARES, Achievements.capFor(-3), "negative counts clamp to base");
    }

    @Test
    void floorGrowsEveryThreeUnlocks() {
        assertEquals(0, Achievements.floorFor(0));
        assertEquals(0, Achievements.floorFor(2));
        assertEquals(1, Achievements.floorFor(3));
        assertEquals(2, Achievements.floorFor(6));
        assertEquals(0, Achievements.floorFor(-9), "negative counts clamp to no floor");
    }

    @Test
    void byIdFindsKnownAndNullsUnknown() {
        assertNotNull(Achievements.byId("employee_one"));
        assertNull(Achievements.byId("nope_not_real"));
    }

    @Test
    void theTwoEasterEggsAreSecretAndTheRestAreNot() {
        assertTrue(Achievements.byId("employee_one").secret());
        assertTrue(Achievements.byId("the_whole_tragedy").secret());
        assertFalse(Achievements.byId("margin_call").secret());
        assertFalse(Achievements.byId("buchmann_grad").secret());
    }
}
