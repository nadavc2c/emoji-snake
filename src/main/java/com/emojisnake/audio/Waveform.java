package com.emojisnake.audio;

/**
 * The oscillator shapes available to a {@link Voice}. These are the classic chip timbres:
 * a naive (aliasing) square - whose grit suits the hostile aesthetic - plus triangle and saw,
 * and an LFSR pseudo-noise channel for percussion. Sampling is deliberately raw (no
 * band-limiting) because the harsh edges are the point.
 */
public enum Waveform {
    SQUARE,
    TRIANGLE,
    SAW,
    NOISE
}
