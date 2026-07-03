package com.emojisnake;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The single save file. Everything persistent lives in ONE working-directory file, {@code save.dat}
 * (obfuscated + tamper-evident via {@link SaveCodec}), so deleting that one file resets the player to
 * a clean slate:
 * <ul>
 *   <li>{@code highScore} - best score;</li>
 *   <li>{@code gamesPlayed} - drives the slow "wake up" reveal;</li>
 *   <li>{@code rank} - back-room floors descended (roguelite meta-progression);</li>
 *   <li>{@code maxLifeBonus} - permanent +max-life upgrades banked;</li>
 *   <li>{@code ended} - reached the true ending;</li>
 *   <li>{@code trim} - banked BARBER "haircut" levels (persistent survival upgrade).</li>
 * </ul>
 * On first load it migrates (and removes) the old split files {@code highscore.txt}/{@code progress.txt}/
 * {@code meta.txt} if present. Fails silent on I/O errors.
 */
public final class SaveStore {

    /** Immutable snapshot of all persisted state. {@code trim} = banked BARBER "haircut" levels. */
    public record Save(int rank, int maxLifeBonus, boolean ended,
                       int highScore, int gamesPlayed, int trim) {
        public Save(int rank, int maxLifeBonus, boolean ended) {
            this(rank, maxLifeBonus, ended, 0, 0, 0);
        }
        public Save withRank(int r) { return new Save(r, maxLifeBonus, ended, highScore, gamesPlayed, trim); }
        public Save withMaxLifeBonus(int b) { return new Save(rank, b, ended, highScore, gamesPlayed, trim); }
        public Save withEnded(boolean e) { return new Save(rank, maxLifeBonus, e, highScore, gamesPlayed, trim); }
        public Save withHighScore(int h) { return new Save(rank, maxLifeBonus, ended, h, gamesPlayed, trim); }
        public Save withGamesPlayed(int g) { return new Save(rank, maxLifeBonus, ended, highScore, g, trim); }
        public Save withTrim(int t) { return new Save(rank, maxLifeBonus, ended, highScore, gamesPlayed, t); }
    }

    private final Path file;

    public SaveStore() {
        this(Path.of(System.getProperty("user.dir"), "save.dat"));
    }

    public SaveStore(Path file) {
        this.file = file;
    }

    public Save load() {
        try {
            if (Files.exists(file)) {
                String decoded = SaveCodec.decode(Files.readString(file));
                if (decoded != null) {
                    return parse(decoded);
                }
                // A failed digest means corruption or tampering - never parse it as plain text
                // (every real version has always written save.dat encoded). Fresh slate instead.
                return new Save(0, 0, false);
            }
            return migrateLegacy(); // first run on this version: fold in the old split files (if any)
        } catch (IOException | NumberFormatException ignored) {
            return new Save(0, 0, false);
        }
    }

    public void save(Save s) {
        String encoded = SaveCodec.encode(
                "high=" + s.highScore() + "\ngames=" + s.gamesPlayed()
                + "\nrank=" + s.rank() + "\nmaxlife=" + s.maxLifeBonus()
                + "\nended=" + s.ended() + "\ntrim=" + s.trim() + "\n");
        try {
            // Write-then-move so a mid-write kill can't truncate the only copy of the save.
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, encoded);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // Best-effort persistence: as a last resort (e.g. an AV briefly locking the move target),
            // fall back to the direct write rather than silently saving nothing.
            try {
                Files.writeString(file, encoded);
            } catch (IOException alsoIgnored) {
                // give up quietly - same contract as before
            }
        }
    }

    private Save parse(String content) {
        int high = 0;
        int games = 0;
        int rank = 0;
        int bonus = 0;
        int trim = 0;
        boolean ended = false;
        for (String line : content.split("\n")) {
            int eq = line.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String val = line.substring(eq + 1).trim();
            // Per-key fault tolerance: a save copied from a different version may carry unknown keys
            // (ignored below) or a malformed value; skip only the bad line and keep every field that
            // DOES parse, rather than letting one weird value reset the whole save.
            try {
                switch (key) {
                    case "high" -> high = Integer.parseInt(val);
                    case "games" -> games = Integer.parseInt(val);
                    case "rank" -> rank = Integer.parseInt(val);
                    case "maxlife" -> bonus = Integer.parseInt(val);
                    case "trim" -> trim = Integer.parseInt(val);
                    case "ended" -> ended = Boolean.parseBoolean(val);
                    // "vn" (novels read) was persisted by older versions; novels are per-run now, so it
                    // falls through to the ignore below and an old save still loads cleanly.
                    default -> { /* ignore unknown/legacy keys (compatible with other-version saves) */ }
                }
            } catch (RuntimeException ignored) {
                // malformed value from another version -> drop this key, keep the rest
            }
        }
        return new Save(rank, bonus, ended, high, games, trim);
    }

    /** Fold the pre-consolidation split files into one save.dat, then remove them. */
    private Save migrateLegacy() {
        Path dir = file.getParent() != null ? file.getParent() : Path.of(".");
        int high = legacyInt(dir.resolve("highscore.txt"));
        int games = legacyInt(dir.resolve("progress.txt"));
        Save meta = legacyMeta(dir.resolve("meta.txt"));
        Save merged = new Save(meta.rank(), meta.maxLifeBonus(), meta.ended(), high, games, meta.trim());
        if (high != 0 || games != 0 || merged.rank() != 0 || merged.ended()) {
            save(merged); // there was real legacy data -> write the unified file
        }
        deleteQuietly(dir.resolve("highscore.txt"));
        deleteQuietly(dir.resolve("progress.txt"));
        deleteQuietly(dir.resolve("meta.txt"));
        return merged;
    }

    private int legacyInt(Path p) {
        try {
            if (Files.exists(p)) {
                String raw = Files.readString(p).trim();
                String decoded = SaveCodec.decode(raw);
                return Integer.parseInt((decoded != null ? decoded : raw).trim());
            }
        } catch (IOException | NumberFormatException ignored) {
            // treat as absent
        }
        return 0;
    }

    private Save legacyMeta(Path p) {
        try {
            if (Files.exists(p)) {
                String raw = Files.readString(p);
                String decoded = SaveCodec.decode(raw);
                return parse(decoded != null ? decoded : raw);
            }
        } catch (IOException | NumberFormatException ignored) {
            // treat as absent
        }
        return new Save(0, 0, false);
    }

    private void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }
}
