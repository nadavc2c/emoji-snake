package com.emojisnake;

/** An immutable grid cell coordinate (column x, row y). */
public record Point(int x, int y) {
    /** The neighbouring cell one step in the given direction. */
    public Point step(Direction d) {
        return new Point(x + d.dx(), y + d.dy());
    }
}
