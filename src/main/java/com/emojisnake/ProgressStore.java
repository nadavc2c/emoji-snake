package com.emojisnake;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists cross-run progress - currently just the number of games played, which gates the
 * slow "the game wakes up" reveal: the first couple of runs play straight, then the deranged
 * meta layer (taunts, fake deaths, glitches) switches on. Stored as plain text in the working
 * directory next to {@code highscore.txt}, keeping the whole footprint inside the repo. Fails
 * silent on I/O errors. See {@link HighScoreStore}.
 */
public final class ProgressStore {

    private final Path file;

    public ProgressStore() {
        this(Path.of(System.getProperty("user.dir"), "progress.txt"));
    }

    public ProgressStore(Path file) {
        this.file = file;
    }

    public int loadGamesPlayed() {
        try {
            if (Files.exists(file)) {
                String raw = Files.readString(file).trim();
                String decoded = SaveCodec.decode(raw);
                return Integer.parseInt((decoded != null ? decoded : raw).trim()); // legacy fallback
            }
        } catch (IOException | NumberFormatException ignored) {
            // corrupt/unreadable/tampered -> treat as a fresh player
        }
        return 0;
    }

    public void saveGamesPlayed(int games) {
        try {
            Files.writeString(file, SaveCodec.encode(Integer.toString(games)));
        } catch (IOException ignored) {
            // best-effort persistence
        }
    }
}
