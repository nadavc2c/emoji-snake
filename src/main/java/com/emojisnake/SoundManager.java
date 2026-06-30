package com.emojisnake;

import com.emojisnake.audio.AudioEngine;
import com.emojisnake.audio.MusicScene;
import com.emojisnake.audio.Sfx;

/**
 * Thin facade over the streaming {@link AudioEngine}. Keeps the original tiny API
 * (eat/bonus/crash/toggle/isEnabled) so existing callers in {@link EmojiSnakeApp} are
 * unchanged, while everything underneath is now a runtime square/triangle/noise synth with
 * continuous adaptive chiptune. Still fails silent on machines without audio.
 *
 * <p>New responsibilities vs. the old tone-clip version: it must be {@link #start()}ed once the
 * UI is up, fed an {@link IntensitySnapshot} each frame via {@link #setIntensity}, and
 * {@link #close()}d on app shutdown so the audio thread/line are released cleanly.
 */
public final class SoundManager {

    private static final long DEFAULT_SEED = 0xC0FFEE_5EEDL;

    private final AudioEngine engine;
    private boolean enabled = true;

    public SoundManager() {
        this(DEFAULT_SEED);
    }

    public SoundManager(long seed) {
        this.engine = new AudioEngine(seed);
    }

    /** Begin the continuous music stream (call after the stage is shown). */
    public void start() {
        engine.start();
    }

    /** Push the current madness level to the audio thread (lock-free). */
    public void setIntensity(IntensitySnapshot snapshot) {
        engine.setIntensity(snapshot);
    }

    /** Switch the musical bed for the current context (boss / shop / VN / secret / ending). */
    public void setScene(MusicScene scene) {
        engine.setScene(scene);
    }

    public void eat()   { engine.playSfx(Sfx.EAT); }
    public void bonus() { engine.playSfx(Sfx.BONUS); }
    public void crash() { engine.playSfx(Sfx.CRASH); }
    public void turn()  { engine.playSfx(Sfx.TURN); }

    /** Mute/unmute toggle (M key). Returns the new enabled state. */
    public boolean toggle() {
        enabled = !enabled;
        engine.setEnabled(enabled);
        return enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Release the audio thread + line. Call from {@code Application.stop()}. */
    public void close() {
        engine.close();
    }
}
