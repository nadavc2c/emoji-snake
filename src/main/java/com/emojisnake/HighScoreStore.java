package com.emojisnake;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists the best score to a plain text file. By design the file lives in the working
 * directory (the project folder when launched via Gradle), NOT the user home - keeping the
 * whole footprint inside the repo so the machine stays clean. Fails silent on I/O errors.
 */
public final class HighScoreStore {

    private final Path file;

    public HighScoreStore() {
        this(Path.of(System.getProperty("user.dir"), "highscore.txt"));
    }

    public HighScoreStore(Path file) {
        this.file = file;
    }

    public int load() {
        try {
            if (Files.exists(file)) {
                String raw = Files.readString(file).trim();
                String decoded = SaveCodec.decode(raw);
                return Integer.parseInt((decoded != null ? decoded : raw).trim()); // legacy fallback
            }
        } catch (IOException | NumberFormatException ignored) {
            // corrupt/unreadable/tampered -> treat as no high score
        }
        return 0;
    }

    public void save(int score) {
        try {
            Files.writeString(file, SaveCodec.encode(Integer.toString(score)));
        } catch (IOException ignored) {
            // best-effort persistence
        }
    }
}
