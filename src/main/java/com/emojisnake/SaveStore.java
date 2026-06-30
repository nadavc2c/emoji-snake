package com.emojisnake;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

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
 *   <li>{@code vnDone} - ids of visual novels finished.</li>
 * </ul>
 * On first load it migrates (and removes) the old split files {@code highscore.txt}/{@code progress.txt}/
 * {@code meta.txt} if present. Fails silent on I/O errors.
 */
public final class SaveStore {

    /** Immutable snapshot of all persisted state. */
    public record Save(int rank, int maxLifeBonus, boolean ended, Set<String> vnDone,
                       int highScore, int gamesPlayed) {
        public Save {
            vnDone = (vnDone == null) ? Set.of() : Set.copyOf(vnDone);
        }
        public Save(int rank, int maxLifeBonus, boolean ended, Set<String> vnDone) {
            this(rank, maxLifeBonus, ended, vnDone, 0, 0);
        }
        public Save(int rank, int maxLifeBonus, boolean ended) {
            this(rank, maxLifeBonus, ended, Set.of(), 0, 0);
        }
        public Save withRank(int r) { return new Save(r, maxLifeBonus, ended, vnDone, highScore, gamesPlayed); }
        public Save withMaxLifeBonus(int b) { return new Save(rank, b, ended, vnDone, highScore, gamesPlayed); }
        public Save withEnded(boolean e) { return new Save(rank, maxLifeBonus, e, vnDone, highScore, gamesPlayed); }
        public Save withHighScore(int h) { return new Save(rank, maxLifeBonus, ended, vnDone, h, gamesPlayed); }
        public Save withGamesPlayed(int g) { return new Save(rank, maxLifeBonus, ended, vnDone, highScore, g); }
        public Save withVnDone(String id) {
            var s = new HashSet<>(vnDone);
            s.add(id);
            return new Save(rank, maxLifeBonus, ended, s, highScore, gamesPlayed);
        }
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
                return parse(Files.readString(file)); // legacy plain-text fallback
            }
            return migrateLegacy(); // first run on this version: fold in the old split files (if any)
        } catch (IOException | NumberFormatException ignored) {
            return new Save(0, 0, false);
        }
    }

    public void save(Save s) {
        try {
            String content = "high=" + s.highScore() + "\ngames=" + s.gamesPlayed()
                    + "\nrank=" + s.rank() + "\nmaxlife=" + s.maxLifeBonus()
                    + "\nended=" + s.ended() + "\nvn=" + String.join(",", s.vnDone()) + "\n";
            Files.writeString(file, SaveCodec.encode(content));
        } catch (IOException ignored) {
            // best-effort persistence
        }
    }

    private Save parse(String content) {
        int high = 0;
        int games = 0;
        int rank = 0;
        int bonus = 0;
        boolean ended = false;
        var vnDone = new HashSet<String>();
        for (String line : content.split("\n")) {
            int eq = line.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String val = line.substring(eq + 1).trim();
            switch (key) {
                case "high" -> high = Integer.parseInt(val);
                case "games" -> games = Integer.parseInt(val);
                case "rank" -> rank = Integer.parseInt(val);
                case "maxlife" -> bonus = Integer.parseInt(val);
                case "ended" -> ended = Boolean.parseBoolean(val);
                case "vn" -> {
                    for (String id : val.split(",")) {
                        if (!id.isBlank()) {
                            vnDone.add(id.trim());
                        }
                    }
                }
                default -> { /* ignore unknown keys */ }
            }
        }
        return new Save(rank, bonus, ended, vnDone, high, games);
    }

    /** Fold the pre-consolidation split files into one save.dat, then remove them. */
    private Save migrateLegacy() {
        Path dir = file.getParent() != null ? file.getParent() : Path.of(".");
        int high = legacyInt(dir.resolve("highscore.txt"));
        int games = legacyInt(dir.resolve("progress.txt"));
        Save meta = legacyMeta(dir.resolve("meta.txt"));
        Save merged = new Save(meta.rank(), meta.maxLifeBonus(), meta.ended(), meta.vnDone(), high, games);
        if (high != 0 || games != 0 || merged.rank() != 0 || merged.ended() || !merged.vnDone().isEmpty()) {
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
