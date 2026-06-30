package com.emojisnake.vn;

/**
 * A live cursor walking a {@link Story}: tracks the current node and advances on {@link #choose}.
 * Pure logic - the interlude reads {@link #current()} to render and calls {@link #choose} on input.
 */
public final class StoryState {

    private final Story story;
    private StoryNode current;

    public StoryState(Story story) {
        this.story = story;
        this.current = story.start();
    }

    public Story story() { return story; }

    public StoryNode current() { return current; }

    /** True while the current node still offers choices. */
    public boolean awaitingChoice() {
        return !current.isEnding();
    }

    /** True once a terminal node is reached (safe or lethal). */
    public boolean ended() {
        return current.isEnding();
    }

    /** True when the reached ending kills the player. */
    public boolean died() {
        return current.isEnding() && current.lethal();
    }

    /** Score paid out by the reached (safe) ending; 0 if not ended or lethal. */
    public int reward() {
        return current.isEnding() && !current.lethal() ? current.reward() : 0;
    }

    /** Take choice {@code i} of the current node (no-op if out of range or already ended). */
    public void choose(int i) {
        if (current.isEnding()) {
            return;
        }
        var choices = current.choices();
        if (i < 0 || i >= choices.size()) {
            return;
        }
        StoryNode next = story.node(choices.get(i).target());
        if (next != null) {
            current = next;
        }
    }
}
