package com.emojisnake.mode;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;

/**
 * A short takeover state that fully owns input + rendering for a moment - the "what the hell is
 * happening" surprises: a genre-shift mini-game, a fake crash, etc. The app suspends the normal
 * Snake loop while one is active, delegates to it, and resumes when {@link #isDone()} is true.
 *
 * <p>This is the lightweight stand-in for a full mode stack: it covers the two surprise interludes
 * we need without restructuring the whole app, and can be promoted to a proper {@code ModeManager}
 * later if more modes pile up.
 */
public interface Interlude {

    void update(double dt);

    /** Draw the whole screen onto the overlay canvas (the normal board is not drawn under it). */
    void render(GraphicsContext gc, double width, double height);

    void handleKey(KeyCode code);

    boolean isDone();
}
