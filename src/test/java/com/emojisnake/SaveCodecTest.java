package com.emojisnake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The save-file obfuscation: opaque + tamper-evident, with a clean reject for edited/foreign tokens. */
class SaveCodecTest {

    @Test
    void roundTripsExactly() {
        String s = "rank=3\nmaxlife=2\nended=true\n";
        assertEquals(s, SaveCodec.decode(SaveCodec.encode(s)));
    }

    @Test
    void theEncodedFormHidesThePlainValue() {
        String tok = SaveCodec.encode("198");
        assertFalse(tok.contains("198"), "the value must not be readable in the file");
        assertTrue(tok.contains("."), "it carries a verification tag");
    }

    @Test
    void anEditedTokenIsRejected() {
        String tok = SaveCodec.encode("198");
        char[] c = tok.toCharArray();
        c[0] = (c[0] == 'A') ? 'B' : 'A'; // tamper with the payload
        assertNull(SaveCodec.decode(new String(c)), "hand-editing the save must be detected");
    }

    @Test
    void rawLegacyTextIsNotAValidToken() {
        assertNull(SaveCodec.decode("198"), "plain text isn't our format (stores handle legacy separately)");
        assertNull(SaveCodec.decode(null));
        assertNull(SaveCodec.decode("nodot"));
    }
}
