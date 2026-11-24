package com.simpleapplock;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Salted PBKDF2 hashing so the PIN itself is never stored on the phone. */
final class PinHasher {
    private static final int ITERATIONS = 10_000;
    private static final int KEY_BITS = 256;

    private PinHasher() {}

    static String newSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    static String hash(String pin, String saltB64) {
        byte[] salt = Base64.getDecoder().decode(saltB64);
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS);
        try {
            byte[] out = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        } finally {
            spec.clearPassword();
        }
    }

    static boolean matches(String pin, String saltB64, String expectedHashB64) {
        if (pin == null || saltB64 == null || expectedHashB64 == null) return false;
        byte[] actual = Base64.getDecoder().decode(hash(pin, saltB64));
        byte[] expected = Base64.getDecoder().decode(expectedHashB64);
        return MessageDigest.isEqual(actual, expected);
    }
}
