package com.emojisnake.audio;

/**
 * One-shot sound effects, defined purely as synthesis parameters (start/end frequency sweep,
 * waveform, duration, level) so they need no audio files. {@link Mixer#triggerSfx} renders them
 * through the same voice pool as the music, which lets them sit in the mix and be distorted by
 * the same master chain as corruption rises.
 */
public enum Sfx {
    /** Crisp ascending blip when eating normal food. */
    EAT(Waveform.SQUARE, 680, 1020, 0.085, 0.40),
    /** Brighter, longer rising chirp for the bonus gem. */
    BONUS(Waveform.SQUARE, 720, 1500, 0.22, 0.45),
    /** Harsh descending saw "bonk" on death. */
    CRASH(Waveform.SAW, 340, 70, 0.42, 0.6),
    /** Subtle tick on a steering input. */
    TURN(Waveform.TRIANGLE, 280, 300, 0.025, 0.18);

    final Waveform waveform;
    final double startHz;
    final double endHz;
    final double seconds;
    final double level;

    Sfx(Waveform waveform, double startHz, double endHz, double seconds, double level) {
        this.waveform = waveform;
        this.startHz = startHz;
        this.endHz = endHz;
        this.seconds = seconds;
        this.level = level;
    }
}
