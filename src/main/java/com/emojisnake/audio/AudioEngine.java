package com.emojisnake.audio;

import com.emojisnake.IntensitySnapshot;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

/**
 * The real-time audio engine: a single {@link SourceDataLine} fed by a dedicated daemon thread
 * that continuously renders the adaptive {@link Sequencer} + {@link Mixer} into PCM. The game
 * (JavaFX) thread only ever touches this through lock-free hand-offs:
 *
 * <ul>
 *   <li>{@link #setIntensity} stores the latest {@link IntensitySnapshot} in an
 *       {@code AtomicReference} (last-writer-wins);</li>
 *   <li>{@link #playSfx} offers onto a bounded queue (dropped if full - never blocks the game).</li>
 * </ul>
 *
 * <p>Fails silent if no audio device is available (same contract as the original SoundManager),
 * and shuts the thread + line down cleanly from {@code Application.stop()}.
 */
public final class AudioEngine {

    private static final float SAMPLE_RATE = 44_100f;
    private static final int BLOCK_FRAMES = 1024; // ~23 ms per block

    private final Mixer mixer = new Mixer();
    private final Sequencer sequencer;

    private final AtomicReference<IntensitySnapshot> intensityRef =
            new AtomicReference<>(IntensitySnapshot.CALM);
    private final AtomicReference<MusicScene> sceneRef = new AtomicReference<>(MusicScene.NORMAL);
    private final ArrayBlockingQueue<Sfx> sfxQueue = new ArrayBlockingQueue<>(32);

    private volatile boolean running;
    private volatile boolean enabled = true;
    private boolean available;

    private SourceDataLine line;
    private Thread thread;

    public AudioEngine(long seed) {
        this.sequencer = new Sequencer(mixer, SAMPLE_RATE, seed);
        try {
            AudioFormat fmt = new AudioFormat(SAMPLE_RATE, 16, 2, true, false); // signed, little-endian
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
            if (!AudioSystem.isLineSupported(info)) {
                available = false;
                return;
            }
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(fmt, BLOCK_FRAMES * 4 * 4); // a few blocks of latency
            line.start();
            available = true;
        } catch (Exception e) {
            available = false; // headless / no mixer - game stays fully playable, just silent
        }
    }

    /** Begin streaming (no-op if there's no audio device). */
    public void start() {
        if (!available || thread != null) {
            return;
        }
        running = true;
        thread = new Thread(this::renderLoop, "crazy-snake-audio");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY + 2);
        thread.start();
    }

    /** Hand the latest intensity/corruption to the audio thread (lock-free, called per frame). */
    public void setIntensity(IntensitySnapshot snapshot) {
        intensityRef.set(snapshot);
    }

    /** Switch the musical bed (NORMAL groove vs a context's classical score). Lock-free. */
    public void setScene(MusicScene scene) {
        sceneRef.set(scene);
    }

    /** Queue a one-shot SFX; silently dropped if the queue is momentarily full. */
    public void playSfx(Sfx sfx) {
        sfxQueue.offer(sfx);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAvailable() {
        return available;
    }

    /** Stop the thread and release the line. Safe to call once, from {@code Application.stop()}. */
    public void close() {
        running = false;
        if (thread != null) {
            try {
                thread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
        if (line != null) {
            line.flush();
            line.close();
            line = null;
        }
    }

    private void renderLoop() {
        final int frames = BLOCK_FRAMES;
        final float[] fbuf = new float[frames * 2];
        final byte[] bytes = new byte[frames * 2 * 2];

        while (running) {
            IntensitySnapshot snap = intensityRef.get();

            // Drain pending SFX (discard while muted so they don't burst on unmute).
            Sfx sfx;
            while ((sfx = sfxQueue.poll()) != null) {
                if (enabled) {
                    mixer.triggerSfx(sfx, SAMPLE_RATE);
                }
            }

            Arrays.fill(fbuf, 0f);
            if (enabled) {
                sequencer.setScene(sceneRef.get());
                sequencer.setIntensity(snap);
                sequencer.renderInto(fbuf, frames);
                mixer.applyMaster(fbuf, 0, frames, snap.corruption());
            }

            for (int i = 0; i < fbuf.length; i++) {
                double s = fbuf[i];
                if (s > 1.0) s = 1.0;
                else if (s < -1.0) s = -1.0;
                int v = (int) (s * 32767);
                bytes[i * 2] = (byte) (v & 0xff);
                bytes[i * 2 + 1] = (byte) ((v >> 8) & 0xff);
            }

            line.write(bytes, 0, bytes.length); // blocking write paces the loop
        }
    }
}
