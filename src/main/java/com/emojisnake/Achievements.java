package com.emojisnake;

import java.util.List;
import java.util.Set;

/**
 * The cookie-clicker achievement catalog - pure data, no UI, so it's unit-testable and shared by the
 * app (unlock logic + the 📈 stock reward) and the {@code AchievementsInterlude} (the press-{@code X}
 * viewer). Unlocked ids are persisted in {@link SaveStore.Save#achievements()}.
 *
 * <p><b>The reward ("milk"):</b> every unlocked achievement permanently boosts the 📈 stock system -
 * it raises the portfolio <em>ceiling</em> ({@link #capFor}, above the base {@value #BASE_MAX_SHARES})
 * <em>and</em> a crash-proof <em>floor</em> ({@link #floorFor}) a MARGIN CALL can't drop you below. So
 * the more of the game's gags you master, the fatter (and safer) your rally.
 *
 * <p>{@code secret} achievements render as {@code ??? - <hint>} until unlocked (the easter eggs). The
 * unlock <em>hooks</em> live in {@code EmojiSnakeApp} (each id is unlocked at the matching game event).
 */
public final class Achievements {

    /** The base portfolio cap when zero achievements are unlocked (matches {@code GameState}'s default). */
    public static final int BASE_MAX_SHARES = 12;

    /** One achievement: a stable {@code id} (persisted), a {@code title}, a one-line {@code hint}, and
     *  whether it's a {@code secret} (shown as {@code ???} until earned). */
    public record Achievement(String id, String title, String hint, boolean secret) {
    }

    /** The catalog, in display order. Ids are lowercase_underscore (safe in the comma-joined save line). */
    public static final List<Achievement> ALL = List.of(
            new Achievement("margin_call", "Margin Call", "Survive a market crash", false),
            new Achievement("bull_market", "Bull Market", "Hold 12 shares at once", false),
            new Achievement("jackpot", "Jackpot", "Win big at the slots", false),
            new Achievement("cockatrice", "Cockatrice", "Become the chicken", false),
            new Achievement("fresh_cut", "Fresh Cut", "Visit THE BARBER", false),
            new Achievement("vested", "Vested Interest", "Bank a permanent extra life", false),
            new Achievement("well_read", "Well-Read", "Read all 5 files in one run", false),
            new Achievement("untouchable", "Untouchable", "Dodge a boss flawlessly", false),
            new Achievement("buchmann_grad", "Buchmann Graduate", "Clear the faculty", false),
            new Achievement("sub_basement", "Sub-Basement", "Reach floor 5 in one run", false),
            new Achievement("become_firm", "You Are The Firm", "Beat the game", false),
            new Achievement("konami", "↑↑↓↓←→←→BA", "There's a code. Of course there's a code.", true),
            new Achievement("the_whole_tragedy", "The Whole Tragedy", "Ruin every romance in a single run", true),
            new Achievement("employee_one", "Employee One", "Read what only the cursed can read", true));

    private Achievements() {
    }

    /** Number of achievements in the catalog. */
    public static int count() {
        return ALL.size();
    }

    /**
     * How many CATALOG achievements the given unlocked set holds - ignores ids this build doesn't know
     * (a save.dat written by a newer version). Used everywhere the count drives the 📈 reward / HUD, so a
     * cross-version save never inflates the ceiling/floor or desyncs the {@code n/N} readout from the viewer.
     */
    public static int countUnlocked(Set<String> unlocked) {
        if (unlocked == null) {
            return 0;
        }
        int n = 0;
        for (Achievement a : ALL) {
            if (unlocked.contains(a.id())) {
                n++;
            }
        }
        return n;
    }

    /** Look up an achievement by id, or {@code null} if unknown (e.g. a legacy id from another version). */
    public static Achievement byId(String id) {
        for (Achievement a : ALL) {
            if (a.id().equals(id)) {
                return a;
            }
        }
        return null;
    }

    /** The 📈 portfolio ceiling for a player who has unlocked {@code unlockedCount} achievements: base + 1 each. */
    public static int capFor(int unlockedCount) {
        return BASE_MAX_SHARES + Math.max(0, unlockedCount);
    }

    /** The crash-proof 📈 share floor: grows by 1 every 3 achievements unlocked. */
    public static int floorFor(int unlockedCount) {
        return Math.max(0, unlockedCount) / 3;
    }
}
