package iq.haji.alhjexx.security;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PinHasherTest {

    @Test
    public void hashIsNotPlainAndVerifies() {
        String salt = PinHasher.newSalt();
        String hash = PinHasher.hash("4321", salt);
        assertNotEquals("4321", hash);
        assertTrue(PinHasher.verify("4321", salt, hash));
        assertFalse(PinHasher.verify("1234", salt, hash));
    }

    @Test
    public void pinRules() {
        assertTrue(PinHasher.isValidPin("1234"));
        assertFalse(PinHasher.isValidPin("123"));
        assertFalse(PinHasher.isValidPin("12a4"));
    }
}
