package iq.haji.alhjexx.security;

import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * تخزين رموز PIN بشكل غير مكشوف: PBKDF2-HMAC-SHA256 مع ملح عشوائي لكل مستخدم.
 * لا يُخزَّن الرمز نفسه في أي مكان.
 */
public final class PinHasher {

    private static final int ITERATIONS = 60_000;
    private static final int KEY_BITS = 256;

    private PinHasher() {
    }

    public static boolean isValidPin(String pin) {
        if (pin == null || pin.length() < 4 || pin.length() > 8) {
            return false;
        }
        for (int i = 0; i < pin.length(); i++) {
            if (pin.charAt(i) < '0' || pin.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }

    public static String newSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return toHex(salt);
    }

    public static String hash(String pin, String saltHex) {
        try {
            KeySpec spec = new PBEKeySpec(pin.toCharArray(), fromHex(saltHex), ITERATIONS, KEY_BITS);
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return toHex(f.generateSecret(spec).getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException("تعذر معالجة الرمز", e);
        }
    }

    /** مقارنة بزمن ثابت. */
    public static boolean verify(String pin, String saltHex, String expectedHash) {
        if (pin == null || saltHex == null || expectedHash == null) {
            return false;
        }
        String actual = hash(pin, saltHex);
        if (actual.length() != expectedHash.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < actual.length(); i++) {
            diff |= actual.charAt(i) ^ expectedHash.charAt(i);
        }
        return diff == 0;
    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) {
            sb.append(String.format("%02x", x & 0xff));
        }
        return sb.toString();
    }

    private static byte[] fromHex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
