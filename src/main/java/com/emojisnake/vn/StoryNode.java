package com.emojisnake.vn;

import java.util.List;

/**
 * One beat of a visual novel: who is talking ({@code speaker} + {@code portrait}/{@code expr}), the
 * {@code background} behind them, the line of {@code text}, and the {@code choices} branching out of
 * it. A node with no choices is an <em>ending</em>; {@link #lethal()} marks an ending that kills the
 * player (costs a life - reserved for super-kind choices), and {@link #reward()} is the score a safe
 * ending pays out (big for ruthless wins, 1 for a meek/neutral exit). {@code portrait} may be blank
 * for narration with no character on screen.
 */
public record StoryNode(String id, String speaker, String portrait, String expr,
                        String background, String text, List<Choice> choices, boolean lethal, int reward) {

    public StoryNode {
        choices = (choices == null) ? List.of() : List.copyOf(choices);
    }

    /** A terminal beat (no further choices). */
    public boolean isEnding() {
        return choices.isEmpty();
    }

    /** True when a character portrait should be drawn (blank = pure narration). */
    public boolean hasPortrait() {
        return portrait != null && !portrait.isBlank();
    }
}
