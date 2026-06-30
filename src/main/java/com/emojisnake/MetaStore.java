package com.emojisnake;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists the roguelite meta-progression (Dead-Cells style) across runs: how many back-room floors
 * you've descended ({@code rank}), how many permanent +max-life upgrades you've banked
 * ({@code maxLifeBonus}), and whether you've reached the true ending ({@code ended}). Plain
 * key=value text in the working directory next to {@code progress.txt}, fails silent on I/O errors.
 * See {@link ProgressStore} / {@link HighScoreStore}.
 */
public final class MetaStore {

    /** Immutable snapshot of the persisted meta state. {@code vnDone} = ids of novels finished. */
    public record Meta(int rank, int maxLifeBonus, boolean ended, java.util.Set<String> vnDone) {
        public Meta {
            vnDone = (vnDone == null) ? java.util.Set.of() : java.util.Set.copyOf(vnDone);
        }
        public Meta(int rank, int maxLifeBonus, boolean ended) {
            this(rank, maxLifeBonus, ended, java.util.Set.of());
        }
        public Meta withRank(int r) { return new Meta(r, maxLifeBonus, ended, vnDone); }
        public Meta withMaxLifeBonus(int b) { return new Meta(rank, b, ended, vnDone); }
        public Meta withEnded(boolean e) { return new Meta(rank, maxLifeBonus, e, vnDone); }
        public Meta withVnDone(String id) {
            var s = new java.util.HashSet<>(vnDone);
            s.add(id);
            return new Meta(rank, maxLifeBonus, ended, s);
        }
    }

    private final Path file;

    public MetaStore() {
        this(Path.of(System.getProperty("user.dir"), "meta.txt"));
    }

    public MetaStore(Path file) {
        this.file = file;
    }

    public Meta load() {
        int rank = 0;
        int bonus = 0;
        boolean ended = false;
        var vnDone = new java.util.HashSet<String>();
        try {
            if (Files.exists(file)) {
                String raw = Files.readString(file);
                String decoded = SaveCodec.decode(raw);
                String content = decoded != null ? decoded : raw; // legacy plain-text fallback
                for (String line : content.split("\n")) {
                    int eq = line.indexOf('=');
                    if (eq < 0) {
                        continue;
                    }
                    String key = line.substring(0, eq).trim();
                    String val = line.substring(eq + 1).trim();
                    switch (key) {
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
            }
        } catch (IOException | NumberFormatException ignored) {
            // corrupt/unreadable -> treat as a fresh ladder climber
            return new Meta(0, 0, false);
        }
        return new Meta(rank, bonus, ended, vnDone);
    }

    public void save(Meta m) {
        try {
            String content = "rank=" + m.rank() + "\nmaxlife=" + m.maxLifeBonus()
                    + "\nended=" + m.ended() + "\nvn=" + String.join(",", m.vnDone()) + "\n";
            Files.writeString(file, SaveCodec.encode(content));
        } catch (IOException ignored) {
            // best-effort persistence
        }
    }
}
