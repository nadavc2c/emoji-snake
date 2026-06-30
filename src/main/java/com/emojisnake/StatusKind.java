package com.emojisnake;

/**
 * Timed effects a power-up grants the snake. Each maps from an {@link Element}:
 * <ul>
 *   <li>{@code SLOW} - the board crawls (forgiving + trippy);</li>
 *   <li>{@code GHOST} - pass through your own body and obstacles;</li>
 *   <li>{@code BURN} - destroy obstacles you touch instead of dying;</li>
 *   <li>{@code GOLD} - score counts double;</li>
 *   <li>{@code MAGNET} - food drifts toward your head.</li>
 * </ul>
 */
public enum StatusKind {
    SLOW, GHOST, BURN, GOLD, MAGNET
}
