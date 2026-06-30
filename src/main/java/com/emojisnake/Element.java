package com.emojisnake;

/**
 * A power-up flavour. Each carries its board {@link Tile} and the {@link StatusKind} it grants
 * when eaten. Eating several of the same element triggers a stronger {@code SYNERGY}.
 */
public enum Element {
    FIRE(Tile.FIRE, StatusKind.BURN),
    ICE(Tile.ICE, StatusKind.SLOW),
    GHOST(Tile.GHOST, StatusKind.GHOST),
    GOLD(Tile.GOLD, StatusKind.GOLD),
    MAGNET(Tile.MAGNET, StatusKind.MAGNET);

    public final Tile tile;
    public final StatusKind status;

    Element(Tile tile, StatusKind status) {
        this.tile = tile;
        this.status = status;
    }

    private static final Element[] VALUES = values();

    public static Element random(java.util.Random rng) {
        return VALUES[rng.nextInt(VALUES.length)];
    }
}
