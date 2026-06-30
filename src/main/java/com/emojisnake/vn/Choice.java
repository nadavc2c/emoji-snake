package com.emojisnake.vn;

/**
 * One selectable option at a junction: the {@code label} the player sees, and the {@code target}
 * node id it leads to. The target always resolves to a {@link StoryNode}; a "wrong" choice simply
 * points at a terminal node whose {@link StoryNode#lethal()} flag is set.
 */
public record Choice(String label, String target) {

    public static Choice of(String label, String target) {
        return new Choice(label, target);
    }
}
