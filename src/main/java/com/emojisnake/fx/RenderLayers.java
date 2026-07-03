package com.emojisnake.fx;

import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;

/**
 * The stacked-canvas render target, split into two groups so the trippy effect chain processes
 * only the <em>game content</em>, not the screen-space post:
 *
 * <ul>
 *   <li><b>{@link #content()}</b> - a Group of {bg, trail, entity} that carries the
 *       {@code Effect} chain (hue cycling + bloom) and is shaken/zoomed by the {@link Camera}.</li>
 *   <li><b>overlay</b> - a sibling canvas <em>outside</em> the effect, for the CRT, color wash,
 *       and pause/game-over text. Keeping it out of the chain stops Bloom from re-blooming the
 *       vignette into a giant blob and keeps the CRT rock-steady while the board shakes.</li>
 * </ul>
 *
 * Layer roles: <b>bg</b> opaque background+grass (repainted each frame); <b>trail</b> neon motion
 * smears; <b>entity</b> crisp snake/food/HUD. {@link #root()} (content + overlay) is the Scene
 * root and what {@code --snapshot} captures (node effects render when an ancestor is snapshotted).
 */
public final class RenderLayers {

    private final double width;
    private final double height;
    private final Group root;
    private final Group content;

    private final Canvas bg;
    private final Canvas trail;
    private final Canvas entity;
    private final Canvas overlay;

    private final GraphicsContext bgGc;
    private final GraphicsContext trailGc;
    private final GraphicsContext entityGc;
    private final GraphicsContext overlayGc;

    public RenderLayers(double width, double height) {
        this.width = width;
        this.height = height;

        bg = new Canvas(width, height);
        trail = new Canvas(width, height);
        entity = new Canvas(width, height);
        overlay = new Canvas(width, height);

        bgGc = bg.getGraphicsContext2D();
        trailGc = trail.getGraphicsContext2D();
        entityGc = entity.getGraphicsContext2D();
        overlayGc = overlay.getGraphicsContext2D();
        bgGc.setImageSmoothing(true);
        entityGc.setImageSmoothing(true);

        StackPane stack = new StackPane(bg, trail, entity);
        stack.setMinSize(width, height);
        stack.setPrefSize(width, height);
        stack.setMaxSize(width, height);
        content = new Group(stack);             // effect chain + camera attach here
        root = new Group(content, overlay);     // overlay rides on top, post-effect
    }

    /** Clear a layer to fully transparent (so layers below show through). */
    public void clear(GraphicsContext gc) {
        gc.clearRect(0, 0, width, height);
    }

    /** Clear every layer (bg/trail/entity/overlay) - a blank slate for a takeover screen. */
    public void clearAll() {
        clear(bgGc);
        clear(trailGc);
        clear(entityGc);
        clear(overlayGc);
    }

    public Group root()              { return root; }
    public Group content()           { return content; }
    public GraphicsContext bg()      { return bgGc; }
    public GraphicsContext trail()   { return trailGc; }
    public GraphicsContext entity()  { return entityGc; }
    public GraphicsContext overlay() { return overlayGc; }
}
