package com.emojisnake.mode;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;

/**
 * A short takeover state that fully owns input + rendering for a moment - the "what the hell is
 * happening" surprises: a genre-shift mini-game, a fake crash, etc. The app suspends the normal
 * Snake loop while one is active, delegates to it, and resumes when {@link #isDone()} is true.
 *
 * <p>This is the lightweight stand-in for a full mode stack: it covers the whole surprise roster
 * (boss / fake crash / visual novel / store / slot / secret / ending) without restructuring the
 * app, and can be promoted to a proper {@code ModeManager} later if more modes pile up.
 */
public interface Interlude {

    void update(double dt);

    /** Draw the whole screen onto the overlay canvas (the normal board is not drawn under it). */
    void render(GraphicsContext gc, double width, double height);

    void handleKey(KeyCode code);

    boolean isDone();

    /**
     * The keys that should ADVANCE / dismiss an interlude page. Deliberately NOT "any key": utility
     * keys (mute, screenshot, function keys, ...) must never skip a beat the player is still reading -
     * so a paged screen only moves on a play key (Space / Enter / a direction).
     */
    static boolean isAdvanceKey(KeyCode code) {
        return switch (code) {
            case SPACE, ENTER, UP, DOWN, LEFT, RIGHT, W, A, S, D -> true;
            default -> false;
        };
    }
}
