package com.emojisnake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The single save file: every persisted field (incl. the BARBER haircut level) survives a round trip. */
class SaveStoreTest {

    @Test
    void roundTripsEveryFieldIncludingTrim(@TempDir Path dir) {
        SaveStore store = new SaveStore(dir.resolve("save.dat"));
        store.save(new SaveStore.Save(3, 2, true, 420, 7, 3));

        SaveStore.Save back = store.load();
        assertEquals(3, back.rank());
        assertEquals(2, back.maxLifeBonus());
        assertTrue(back.ended());
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
        new SaveStore(file).save(new SaveStore.Save(1, 1, false, 999, 3, 2));
        String raw = Files.readString(file);
        assertFalse(raw.contains("999"), "the high score must not be readable in the file");
        assertFalse(raw.contains("trim"), "the field keys must not be readable in the file");
    }

    // --- cross-version portability: a save.dat copied from a different version must still load --------

    @Test
    void aSaveFromAnotherVersionLoadsUnknownKeysIgnoredMissingDefaulted(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("save.dat");
        // A save written by a different version: carries an unknown future key, the retired legacy
        // "vn" key (novels are per-run now), AND has no trim field.
        String content = "high=500\ngames=4\nrank=2\nmaxlife=1\nended=true\nvn=a,b\nfuture=42\n";
        Files.writeString(file, SaveCodec.encode(content)); // verbatim-copy: the digest stays valid
        SaveStore.Save back = new SaveStore(file).load();
        assertEquals(500, back.highScore());
        assertEquals(2, back.rank());
        assertTrue(back.ended());
        assertEquals(0, back.trim(), "a missing field defaults; everything known still survives");
    }

    @Test
    void aMalformedFieldIsSkippedNotFatalToTheWholeSave(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("save.dat");
        String content = "high=750\ngames=oops\nrank=3\nended=true\ntrim=2\n"; // games is garbage
        Files.writeString(file, SaveCodec.encode(content));
        SaveStore.Save back = new SaveStore(file).load();
        assertEquals(750, back.highScore(), "a good field survives a sibling's malformed value");
        assertEquals(3, back.rank());
        assertEquals(2, back.trim());
        assertTrue(back.ended());
        assertEquals(0, back.gamesPlayed(), "only the malformed field falls back to its default");
    }

    // --- tamper evidence + crash safety ---------------------------------------------------------

    @Test
    void aHandWrittenPlaintextSaveIsIgnoredNotParsed(@TempDir Path dir) throws Exception {
        // Tamper evidence: no real version ever wrote save.dat as plain text, so a failed digest
        // means editing - the file must be ignored wholesale, not accepted via a plaintext parse.
        Path file = dir.resolve("save.dat");
        Files.writeString(file, "high=999999\nrank=5\nended=true\n");
        SaveStore.Save back = new SaveStore(file).load();
        assertEquals(0, back.highScore(), "an edited save must not smuggle in a high score");
        assertEquals(0, back.rank());
        assertFalse(back.ended());
    }

    @Test
    void aTruncatedSaveFallsBackToACleanSlate(@TempDir Path dir) throws Exception {
        // Crash safety: a half-written file (power loss mid-save) fails the digest and resets
        // cleanly instead of parsing garbage.
        Path file = dir.resolve("save.dat");
        SaveStore store = new SaveStore(file);
        store.save(new SaveStore.Save(3, 2, true, 420, 7, 3));
        String whole = Files.readString(file);
        Files.writeString(file, whole.substring(0, whole.length() / 2));
        SaveStore.Save back = store.load();
        assertEquals(0, back.highScore(), "a truncated save resets rather than mis-parsing");
        assertEquals(0, back.rank());
    }

    @Test
    void savingLeavesNoTempFileBehind(@TempDir Path dir) throws Exception {
        // The atomic write-then-move must clean up after itself and still round-trip.
        Path file = dir.resolve("save.dat");
        SaveStore store = new SaveStore(file);
        store.save(new SaveStore.Save(1, 0, false, 42, 2, 1));
        assertFalse(Files.exists(dir.resolve("save.dat.tmp")), "the staging file must be moved away");
        assertEquals(42, store.load().highScore(), "the moved file round-trips");
    }
}
