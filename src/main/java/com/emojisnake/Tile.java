package com.emojisnake;

import java.util.Random;

/**
 * The kinds of "pixel" the board can show, each backed by a bundled OpenMoji color PNG named by
 * Unicode code point (so emoji always render in full color regardless of the platform font).
 * Beyond the originals this now includes funnier/darker food + obstacle variety, the roguelite
 * power-up elements, portals, and the boss/projectile tiles used by later phases.
 */
public enum Tile {
    EMPTY("1F331"),     // 🌱 seedling - faint background
    HEAD("1F40D"),      // 🐍 snake head
    BODY("1F7E2"),      // 🟢 body segment

    // Food - a random one is chosen per spawn for variety / comedy.
    FOOD_APPLE("1F34E"),   // 🍎
    FOOD_PIZZA("1F355"),   // 🍕
    FOOD_BURGER("1F354"),  // 🍔
    FOOD_TACO("1F32E"),    // 🌮
    FOOD_DONUT("1F369"),   // 🍩
    FOOD_MEAT("1F356"),    // 🍖

    BONUS("1F911"),     // 🤑 money-mouth - time-limited bonus

    BOOK("1F4D6"),      // 📖 rare "bookworm" pickup - opens the slop AI visual novel (kept out of FOODS)
    CHICKEN("1F414"),   // 🐔 the basilisk head (after the roasted-chicken gag)
    EGG("1F95A"),       // 🥚 reverts the basilisk
    STORE("1F3EA"),     // 🏪 on-board shop - bump it to buy power-ups
    SLOT("1F3B0"),      // 🎰 gamble food - eating it adds a random amount of length

    // Living food - only these flee (cooked food can't run). Kept out of FOODS.
    WORM("1FAB1"),      // 🪱
    MOUSE("1F42D"),     // 🐭
    CHICK("1F424"),     // 🐤

    HEART("2764"),      // ❤️ store icon for buying an extra life

    // Obstacles - a dark, varied set (chosen deterministically by cell position).
    OBSTACLE("1F9F1"),  // 🧱 brick
    GRAVE("1FAA6"),     // 🪦 headstone
    SKULL("1F480"),     // 💀 skull

    // Roguelite power-up elements.
    FIRE("1F525"),      // 🔥 burn through obstacles
    ICE("1F9CA"),       // 🧊 slow time
    GHOST("1F47B"),     // 👻 phase through danger
    GOLD("1FA99"),      // 🪙 score multiplier
    MAGNET("1F9F2"),    // 🧲 pull food in

    PORTAL("1F300"),    // 🌀 paired teleport

    // Boss encounter (later phase).
    BOSS("1F479"),      // 👹
    PROJECTILE("1F534"),// 🔴
    BOOM("1F4A5");      // 💥

    private static final Tile[] FOODS =
            {FOOD_APPLE, FOOD_PIZZA, FOOD_BURGER, FOOD_TACO, FOOD_DONUT, FOOD_MEAT};
    private static final Tile[] LIVING_FOODS = {WORM, MOUSE, CHICK};
    private static final Tile[] OBSTACLE_VARIANTS = {OBSTACLE, GRAVE, SKULL};

    private final String codePoint;

    Tile(String codePoint) {
        this.codePoint = codePoint;
    }

    /** Classpath location of the bundled color PNG for this tile. */
    public String resourcePath() {
        return "/emoji/" + codePoint + ".png";
    }

    /** A random food tile (so each apple might be a taco). */
    public static Tile randomFood(Random rng) {
        return FOODS[rng.nextInt(FOODS.length)];
    }

    /** A random living food (worm / mouse / chick) - the only kind that flees. */
    public static Tile randomLiving(Random rng) {
        return LIVING_FOODS[rng.nextInt(LIVING_FOODS.length)];
    }

    /** Deterministic obstacle look per cell, so the board has dark variety without extra state. */
    public static Tile obstacleAt(int x, int y) {
        return OBSTACLE_VARIANTS[Math.floorMod(x * 31 + y, OBSTACLE_VARIANTS.length)];
    }
}
