package com.emojisnake.fx;

import com.emojisnake.Direction;
import com.emojisnake.IntensitySnapshot;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.Blend;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.Bloom;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.ColorInput;
import javafx.scene.effect.DisplacementMap;
import javafx.scene.effect.FloatMap;
import javafx.scene.effect.MotionBlur;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

/**
 * Owns the trippy post-processing for the game-content node. Two parts:
 *
 * <ol>
 *   <li>A GPU <b>node-effect chain</b> built once and mutated per frame:
 *       {@code ColorAdjust (hue/sat) → [DisplacementMap warp] → MotionBlur → Bloom}. The warp is
 *       toggled into the chain only above a corruption threshold so calm play pays nothing.</li>
 *   <li>A gated <b>chromatic aberration</b> pass drawn onto the overlay. This one snapshots the
 *       content (a GPU readback - the documented perf trap), so it is fired only at high
 *       corruption and throttled to every other frame; the capture is reused between.</li>
 * </ol>
 *
 * Everything is JavaFX built-in (no third-party). Calibrated medium-high, not maxed.
 */
public final class FxDirector {

    private static final double WARP_THRESHOLD = 0.3;        // corruption needed to engage warp
    private static final double ABERRATION_THRESHOLD = 0.55; // ...and chromatic aberration
    private static final int WARP_MAP = 100;                 // FloatMap resolution (upscaled smoothly)

    private final double width;
    private final double height;

    private final ColorAdjust colorAdjust = new ColorAdjust();
    private final MotionBlur motionBlur = new MotionBlur();
    private final Bloom bloom = new Bloom(0.5);
    private final DisplacementMap warp = new DisplacementMap();
    private final FloatMap warpMap = new FloatMap(WARP_MAP, WARP_MAP);
    private boolean warpActive;

    private final Blend redChannel = new Blend(BlendMode.MULTIPLY);
    private final Blend cyanChannel = new Blend(BlendMode.MULTIPLY);
    private WritableImage aberrationCapture;

    public FxDirector(Node content, double width, double height) {
        this.width = width;
        this.height = height;

        // Chain wiring (source flows colorAdjust -> warp -> motionBlur -> bloom).
        warp.setInput(colorAdjust);
        motionBlur.setInput(colorAdjust); // warp spliced in only when active
        bloom.setInput(motionBlur);
        content.setEffect(bloom);

        redChannel.setBottomInput(new ColorInput(0, 0, width, height, Color.color(1, 0, 0)));
        cyanChannel.setBottomInput(new ColorInput(0, 0, width, height, Color.color(0, 1, 1)));
    }

    /** Mutate the node-effect chain for this frame. */
    public void update(IntensitySnapshot snap, double phase, Direction dir) {
        double corruption = snap.corruption();
        double intensity = snap.intensity();

        // Hue rotation is the main psychedelic driver (brightness-preserving, no flashbang).
        colorAdjust.setHue(Math.sin(phase) * (0.15 + 0.85 * corruption));
        colorAdjust.setSaturation(corruption * 0.45);
        colorAdjust.setBrightness(corruption > 0.7 ? Math.sin(phase * 9) * 0.05 : 0);

        // Motion blur smears in the travel direction, scaled by speed.
        motionBlur.setAngle(dir == Direction.UP || dir == Direction.DOWN ? 90 : 0);
        motionBlur.setRadius(clamp(intensity * 11, 0, 12));

        // Bloom: lower threshold = more neon, floored so it never blows out.
        bloom.setThreshold(clamp(0.6 - intensity * 0.3, 0.3, 0.9));

        // Heat-haze warp, engaged only when things have rotted enough.
        boolean wantWarp = corruption > WARP_THRESHOLD;
        if (wantWarp) {
            regenWarpMap(phase, corruption);
            if (!warpActive) {
                motionBlur.setInput(warp); // splice warp into the chain
                warpActive = true;
            }
        } else if (warpActive) {
            motionBlur.setInput(colorAdjust); // unsplice - calm play pays nothing
            warpActive = false;
        }
    }

    private void regenWarpMap(double phase, double corruption) {
        // Keep amplitude small: a heat-haze ripple, not big waves. Too much pulls samples past the
        // image edge and carves black scallops out of the border.
        double amp = 0.4 + corruption * 0.3;          // magnitude in [-1,1] space
        double scale = 0.004 + corruption * 0.005;    // ~6px max displacement at full corruption
        for (int y = 0; y < WARP_MAP; y++) {
            for (int x = 0; x < WARP_MAP; x++) {
                float dx = (float) (Math.sin(y * 0.35 + phase * 1.7) * amp);
                float dy = (float) (Math.sin(x * 0.35 + phase * 1.3) * amp);
                warpMap.setSamples(x, y, dx, dy);
            }
        }
        warp.setMapData(warpMap);
        warp.setScaleX(scale);
        warp.setScaleY(scale);
    }

    /**
     * Draw the gated chromatic-aberration ghosts onto {@code overlayGc}. Snapshots {@code content}
     * (perf trap) - so it self-gates on corruption and throttles to every other frame, reusing the
     * capture between. Returns false (no-op) below the threshold.
     */
    public boolean drawAberration(GraphicsContext overlayGc, Node content,
                                  IntensitySnapshot snap, int frame) {
        double corruption = snap.corruption();
        if (corruption < ABERRATION_THRESHOLD) {
            return false;
        }
        if (aberrationCapture == null || frame % 2 == 0) {
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.TRANSPARENT);
            if (aberrationCapture == null) {
                aberrationCapture = new WritableImage((int) width, (int) height);
            }
            content.snapshot(params, aberrationCapture);
        }

        double off = 2 + corruption * 5;
        double alpha = (corruption - ABERRATION_THRESHOLD) / (1 - ABERRATION_THRESHOLD) * 0.6;
        overlayGc.setGlobalBlendMode(BlendMode.SCREEN);
        overlayGc.setGlobalAlpha(alpha);
        overlayGc.setEffect(redChannel);
        overlayGc.drawImage(aberrationCapture, -off, 0, width, height);
        overlayGc.setEffect(cyanChannel);
        overlayGc.drawImage(aberrationCapture, off, 0, width, height);
        overlayGc.setEffect(null);
        overlayGc.setGlobalAlpha(1.0);
        overlayGc.setGlobalBlendMode(BlendMode.SRC_OVER);
        return true;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
