package com.emojisnake.audio;

/**
 * Which musical bed the {@link Sequencer} should play. NORMAL is the adaptive chiptune groove that
 * reacts to intensity/corruption; the rest are context interludes that each play a recognizable
 * public-domain classical melody as note data through the same synth (no audio files):
 *
 * <ul>
 *   <li>{@code BOSS} - "Dies Irae" (doom), driving + distorted.</li>
 *   <li>{@code SHOP} - Beethoven's "Für Elise" (classy, relaxed).</li>
 *   <li>{@code VN} - Pachelbel's Canon in D (romantic, calm) for the dating-sim.</li>
 *   <li>{@code SECRET} - an eerie whole-tone descent.</li>
 *   <li>{@code ENDING} - Beethoven's "Ode to Joy" (triumphant, darkly ironic).</li>
 *   <li>{@code CRASH} - NOT music: a harsh, glitchy system-error drone for the fake-crash gag
 *       (the melodic bed cuts out and this ugly buzz takes over - "just a crash sound").</li>
 * </ul>
 */
public enum MusicScene {
    NORMAL,
    BOSS,
    SHOP,
    VN,
    SECRET,
    ENDING,
    CRASH
}
