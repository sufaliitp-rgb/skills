package com.simpleapplock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PinHasherTest {
    @Test
    public void rightPinMatches() {
        String salt = PinHasher.newSalt();
        String hash = PinHasher.hash("2580", salt);
        assertTrue(PinHasher.matches("2580", salt, hash));
    }

    @Test
    public void wrongPinDoesNotMatch() {
        String salt = PinHasher.newSalt();
        String hash = PinHasher.hash("2580", salt);
        assertFalse(PinHasher.matches("2581", salt, hash));
        assertFalse(PinHasher.matches("258", salt, hash));
        assertFalse(PinHasher.matches("", salt, hash));
        assertFalse(PinHasher.matches(null, salt, hash));
    }

    @Test
    public void saltMakesHashesDifferent() {
        assertNotEquals(PinHasher.hash("1234", PinHasher.newSalt()), PinHasher.hash("1234", PinHasher.newSalt()));
    }

    @Test
    public void pinIsNotStoredInPlainText() {
        String salt = PinHasher.newSalt();
        assertFalse(PinHasher.hash("123456", salt).contains("123456"));
    }
}
