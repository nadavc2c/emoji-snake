package com.emojisnake;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Light tamper-resistance for the single save file ({@code save.dat}). This is NOT real
 * cryptographic security - the key ships inside the binary - but it makes the file opaque (XOR +
 * Base64) and tamper-EVIDENT: a truncated keyed digest is appended, so hand-editing (or a truncated
 * write) breaks the digest and the game quietly ignores the file, starting from a clean slate.
 * {@code SaveStore.migrateLegacy} still decodes the pre-consolidation split files on first load.
 */
public final class SaveCodec {

    private static final byte[] KEY =
            "emoji-snake::vested-interests::billing".getBytes(StandardCharsets.UTF_8);
    private static final String SALT = "it-was-always-billing";

    private SaveCodec() {
    }

    /** An opaque, tamper-evident token encoding {@code plain}. */
    public static String encode(String plain) {
        String body = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(xor(plain.getBytes(StandardCharsets.UTF_8)));
        return body + "." + mac(plain);
    }

    /** The original string, or {@code null} if the token is absent / malformed / tampered. */
    public static String decode(String token) {
        if (token == null) {
            return null;
        }
        String t = token.trim();
        int dot = t.lastIndexOf('.');
        if (dot < 0) { // no separator -> not our format (e.g. legacy plain text)
            return null;
        }
        try {
            byte[] x = Base64.getUrlDecoder().decode(t.substring(0, dot));
            String plain = new String(xor(x), StandardCharsets.UTF_8);
            return mac(plain).equals(t.substring(dot + 1)) ? plain : null;
        } catch (RuntimeException e) {
            return null; // not our format / corrupted
        }
    }

    private static byte[] xor(byte[] in) {
        byte[] out = new byte[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = (byte) (in[i] ^ KEY[i % KEY.length]);
        }
        return out;
    }

    private static String mac(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256")
                    .digest((SALT + s + SALT).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(d).substring(0, 16);
        } catch (Exception e) {
            return "x"; // digest unavailable -> a constant tag (still obfuscated, just not verified)
        }
    }
}
