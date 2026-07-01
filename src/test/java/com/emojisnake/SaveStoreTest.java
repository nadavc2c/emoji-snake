package com.emojisnake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The single save file: every persisted field (incl. the BARBER haircut level) survives a round trip. */
class SaveStoreTest {

    @Test
    void roundTripsEveryFieldIncludingTrim(@TempDir Path dir) {
        SaveStore store = new SaveStore(dir.resolve("save.dat"));
        store.save(new SaveStore.Save(3, 2, true, Set.of("a", "b"), 420, 7, 3));

        SaveStore.Save back = store.load();
        assertEquals(3, back.rank());
        assertEquals(2, back.maxLifeBonus());
        assertTrue(back.ended());
        assertEquals(Set.of("a", "b"), back.vnDone());
        assertEquals(420, back.highScore());
        assertEquals(7, back.gamesPlayed());
        assertEquals(3, back.trim(), "the banked haircut level must survive a save/load");
    }

    @Test
    void anAbsentSaveStartsCleanWithNoTrim(@TempDir Path dir) {
        SaveStore.Save fresh = new SaveStore(dir.resolve("save.dat")).load();
        assertEquals(0, fresh.trim(), "a fresh player has no haircut banked");
        assertEquals(0, fresh.rank());
    }

    @Test
    void theSavedFileIsOpaqueOnDisk(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("save.dat");
        new SaveStore(file).save(new SaveStore.Save(1, 1, false, Set.of(), 999, 3, 2));
        String raw = Files.readString(file);
        assertFalse(raw.contains("999"), "the high score must not be readable in the file");
        assertFalse(raw.contains("trim"), "the field keys must not be readable in the file");
    }
}
