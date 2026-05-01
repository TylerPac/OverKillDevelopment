package dev.tylerpac.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.JwtException;

class JwtUtilTest {

    private static final String VALID_SECRET = "this_is_a_very_secure_secret_key_with_at_least_32_characters";
    private static final String WEAK_SECRET = "weak";
    private static final String TEST_USERNAME = "testuser";
    private static final long ONE_HOUR_MS = 3600_000L;

    private final JwtUtil jwtUtil = new JwtUtil(VALID_SECRET, false);

    // -------------------------------------------------------------------------
    // Constructor Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should initialize successfully with valid secret")
    void shouldInitializeWithValidSecret() {
        assertNotNull(new JwtUtil(VALID_SECRET, false));
    }

    @Test
    @DisplayName("Should throw exception when secret is null")
    void shouldThrowExceptionWhenSecretIsNull() {
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> new JwtUtil(null, false)
        );
        assertEquals("JWT_SECRET is required.", ex.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when secret is blank")
    void shouldThrowExceptionWhenSecretIsBlank() {
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> new JwtUtil("   ", false)
        );
        assertEquals("JWT_SECRET is required.", ex.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when secret is too short")
    void shouldThrowExceptionWhenSecretIsTooShort() {
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> new JwtUtil("short", false)
        );
        assertEquals(
            "JWT_SECRET must be at least 32 chars and not use placeholder values.",
            ex.getMessage()
        );
    }

    @Test
    @DisplayName("Should throw exception when secret contains placeholder")
    void shouldThrowExceptionWhenSecretContainsPlaceholder() {
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> new JwtUtil("please_change_me_this_is_a_long_secret_but_has_placeholder", false)
        );
        assertEquals(
            "JWT_SECRET must be at least 32 chars and not use placeholder values.",
            ex.getMessage()
        );
    }

    @Test
    @DisplayName("Should allow weak secret when flag is enabled")
    void shouldAllowWeakSecretWhenFlagEnabled() {
        assertNotNull(new JwtUtil(WEAK_SECRET, true));
    }

    // -------------------------------------------------------------------------
    // Token Generation Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should generate valid token with username")
    void shouldGenerateValidToken() {
        String token = jwtUtil.generateToken(TEST_USERNAME, ONE_HOUR_MS);
        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(token.split("\\.").length == 3);
    }

    @Test
    @DisplayName("Should generate tokens for different users")
    void shouldGenerateTokensForDifferentUsers() {
        String token1 = jwtUtil.generateToken("user1", ONE_HOUR_MS);
        String token2 = jwtUtil.generateToken("user2", ONE_HOUR_MS);
        assertNotEquals(token1, token2);
    }

    @Test
    @DisplayName("Should generate tokens with different TTLs")
    void shouldGenerateTokensWithDifferentTTLs() {
        String shortToken = jwtUtil.generateToken(TEST_USERNAME, 1000L);
        String longToken  = jwtUtil.generateToken(TEST_USERNAME, ONE_HOUR_MS);
        assertNotNull(shortToken);
        assertNotNull(longToken);
        assertNotEquals(shortToken, longToken);
    }

    // -------------------------------------------------------------------------
    // Username Extraction Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should extract username from valid token")
    void shouldExtractUsernameFromValidToken() {
        String token = jwtUtil.generateToken(TEST_USERNAME, ONE_HOUR_MS);
        assertEquals(TEST_USERNAME, jwtUtil.extractUsername(token));
    }

    @Test
    @DisplayName("Should extract username with special characters")
    void shouldExtractUsernameWithSpecialCharacters() {
        String specialUsername = "user@email.com";
        String token = jwtUtil.generateToken(specialUsername, ONE_HOUR_MS);
        assertEquals(specialUsername, jwtUtil.extractUsername(token));
    }

    @Test
    @DisplayName("Should throw exception for invalid token format")
    void shouldThrowExceptionForInvalidTokenFormat() {
        JwtException ex1 = assertThrows(JwtException.class, () -> jwtUtil.extractUsername("invalid.token"));
        assertNotNull(ex1);
    }

    @Test
    @DisplayName("Should throw exception for tampered token")
    void shouldThrowExceptionForTamperedToken() {
        String token = jwtUtil.generateToken(TEST_USERNAME, ONE_HOUR_MS);
        String tampered = token.substring(0, token.length() - 5) + "12345";
        JwtException ex2 = assertThrows(JwtException.class, () -> jwtUtil.extractUsername(tampered));
        assertNotNull(ex2);
    }

    // -------------------------------------------------------------------------
    // Token Validation Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should validate valid token")
    void shouldValidateValidToken() {
        String token = jwtUtil.generateToken(TEST_USERNAME, ONE_HOUR_MS);
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    @DisplayName("Should invalidate expired token")
    void shouldInvalidateExpiredToken() throws InterruptedException {
        String token = jwtUtil.generateToken(TEST_USERNAME, 100L);
        Thread.sleep(150L);
        assertFalse(jwtUtil.validateToken(token));
    }

    @Test
    @DisplayName("Should invalidate tampered token")
    void shouldInvalidateTamperedToken() {
        String token = jwtUtil.generateToken(TEST_USERNAME, ONE_HOUR_MS);
        String tampered = token.substring(0, token.length() - 5) + "ABCDE";
        assertFalse(jwtUtil.validateToken(tampered));
    }

    @Test
    @DisplayName("Should invalidate malformed token")
    void shouldInvalidateMalformedToken() {
        assertFalse(jwtUtil.validateToken("not.a.jwt.token"));
    }

    @Test
    @DisplayName("Should invalidate null token")
    void shouldInvalidateNullToken() {
        assertFalse(jwtUtil.validateToken(null));
    }

    @Test
    @DisplayName("Should invalidate empty token")
    void shouldInvalidateEmptyToken() {
        assertFalse(jwtUtil.validateToken(""));
    }

    @Test
    @DisplayName("Should invalidate token signed with different key")
    void shouldInvalidateTokenSignedWithDifferentKey() {
        JwtUtil other = new JwtUtil("another_very_secure_secret_key_with_at_least_32_characters", false);
        String token = other.generateToken(TEST_USERNAME, ONE_HOUR_MS);
        assertFalse(jwtUtil.validateToken(token));
    }

    // -------------------------------------------------------------------------
    // End-to-End Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should complete full token lifecycle")
    void shouldCompleteFullTokenLifecycle() {
        String token = jwtUtil.generateToken(TEST_USERNAME, ONE_HOUR_MS);
        assertTrue(jwtUtil.validateToken(token));
        assertEquals(TEST_USERNAME, jwtUtil.extractUsername(token));
    }

    @Test
    @DisplayName("Should handle multiple users independently")
    void shouldHandleMultipleUsersIndependently() {
        String token1 = jwtUtil.generateToken("user1", ONE_HOUR_MS);
        String token2 = jwtUtil.generateToken("user2", ONE_HOUR_MS);
        assertTrue(jwtUtil.validateToken(token1));
        assertTrue(jwtUtil.validateToken(token2));
        assertEquals("user1", jwtUtil.extractUsername(token1));
        assertEquals("user2", jwtUtil.extractUsername(token2));
        assertNotEquals(token1, token2);
    }
}
