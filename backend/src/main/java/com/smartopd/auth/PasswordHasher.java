package com.smartopd.auth;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * PasswordHasher - Industry-standard cryptographic password hashing using JDK PBKDF2.
 * 
 * ============================================================================
 * OOP & Security Concept: Cryptographic Defense in Depth
 * - Uses PBKDF2WithHmacSHA256 with 120,000 iterations and per-user cryptographic salt.
 * - Compares digests using constant-time equality check (MessageDigest.isEqual) to prevent timing attacks.
 * - Zero external dependencies; uses pure JDK standard crypto libraries.
 * ============================================================================
 */
public class PasswordHasher {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_BYTES = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Generates a cryptographically strong random salt encoded as hexadecimal.
     * 
     * @return hex-encoded salt string
     */
    public static String generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return HexFormat.of().formatHex(salt);
    }

    /**
     * Hashes a plaintext password using the specified salt.
     * 
     * @param password plaintext password
     * @param saltHex hex-encoded salt
     * @return hex-encoded password hash
     */
    public static String hashPassword(String password, String saltHex) {
        if (password == null) {
            throw new IllegalArgumentException("Password cannot be null.");
        }
        if (saltHex == null || saltHex.trim().isEmpty()) {
            throw new IllegalArgumentException("Salt cannot be empty.");
        }
        try {
            byte[] salt = HexFormat.of().parseHex(saltHex);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] hash = skf.generateSecret(spec).getEncoded();
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Cryptographic algorithm unavailable", e);
        }
    }

    /**
     * Verifies a candidate plaintext password against a stored salt and expected hash.
     * Uses constant-time comparison to prevent side-channel timing attacks.
     * 
     * @param candidatePassword entered plaintext password
     * @param saltHex stored salt
     * @param expectedHash stored hash digest
     * @return true if password matches, false otherwise
     */
    public static boolean verify(String candidatePassword, String saltHex, String expectedHash) {
        if (candidatePassword == null || saltHex == null || expectedHash == null) {
            return false;
        }
        try {
            String candidateHash = hashPassword(candidatePassword, saltHex);
            byte[] a = HexFormat.of().parseHex(candidateHash);
            byte[] b = HexFormat.of().parseHex(expectedHash);
            return MessageDigest.isEqual(a, b);
        } catch (Exception e) {
            return false;
        }
    }
}
