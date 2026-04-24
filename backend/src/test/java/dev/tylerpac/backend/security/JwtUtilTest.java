package dev.tylerpac.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.JwtException;

/**
 * Unit tests for JwtUtil - JWT token generation and validation
 */
class JwtUtilTest {

    private static final String VALID_SECRET = "this_is_a_very_secure_secret_key_with_at_least_32_characters";
    private static final String WEAK_SECRET = "weak";
    private static final String TEST_USERNAME = "testuser";
    private static final long ONE_HOUR_MS = 3600_000L;

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should initialize successfully with valid secret")
        void shouldInitializeWithValidSecret() {
            // Arrange & Act
            JwtUtil jwtUtil = new JwtUtil(VALID_SECRET, false);

            // Assert
            assertNotNull(jwtUtil);
        }

        @Test
        @DisplayName("Should throw exception when secret is null")
        void shouldThrowExceptionWhenSecretIsNull() {
            // Act & Assert
            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new JwtUtil(null, false)
            );
            assertEquals("JWT_SECRET is required.", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception when secret is blank")
        void shouldThrowExceptionWhenSecretIsBlank() {
            // Act & Assert
            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new JwtUtil("   ", false)
            );
            assertEquals("JWT_SECRET is required.", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception when secret is too short")
        void shouldThrowExceptionWhenSecretIsTooShort() {
            // Act & Assert
            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new JwtUtil("short", false)
            );
            assertEquals(
                "JWT_SECRET must be at least 32 chars and not use placeholder values.",
                exception.getMessage()
            );
        }

        @Test
        @DisplayName("Should throw exception when secret contains placeholder")
        void shouldThrowExceptionWhenSecretContainsPlaceholder() {
            // Act & Assert
            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new JwtUtil("please_change_me_this_is_a_long_secret_but_has_placeholder", false)
            );
            assertEquals(
                "JWT_SECRET must be at least 32 chars and not use placeholder values.",
                exception.getMessage()
            );
        }

        @Test
        @DisplayName("Should allow weak secret when flag is enabled")
        void shouldAllowWeakSecretWhenFlagEnabled() {
            // Act
            JwtUtil jwtUtil = new JwtUtil(WEAK_SECRET, true);

            // Assert
            assertNotNull(jwtUtil);
        }
    }

    @Nested
    @DisplayName("Token Generation Tests")
    class TokenGenerationTests {

        private JwtUtil jwtUtil;

        @org.junit.jupiter.api.BeforeEach
        void setUp() {
            jwtUtil = new JwtUtil(VALID_SECRET, false);
        }

        @Test
        @DisplayName("Should generate valid token with username")
        void shouldGenerateValidToken() {
            // Act
            String token = jwtUtil.generateToken(TEST_USERNAME, ONE_HOUR_MS);

            // Assert
            assertNotNull(token);
            assertFalse(token.isEmpty());
            assertTrue(token.split("\\.").length == 3); // JWT has 3 parts: header.payload.signature
        }

        @Test
        @DisplayName("Should generate tokens for different users")
        void shouldGenerateTokensForDifferentUsers() {
            // Act
            String token1 = jwtUtil.generateToken("user1", ONE_HOUR_MS);
            String token2 = jwtUtil.generateToken("user2", ONE_HOUR_MS);

            // Assert - Different users should have different tokens
            assertNotEquals(token1, token2);
        }

        @Test
        @DisplayName("Should generate tokens with different TTLs")
        void shouldGenerateTokensWithDifferentTTLs() {
            // Act
            String shortToken = jwtUtil.generateToken(TEST_USERNAME, 1000L);
            String longToken = jwtUtil.generateToken(TEST_USERNAME, ONE_HOUR_MS);

            // Assert
            assertNotNull(shortToken);
            assertNotNull(longToken);
            assertNotEquals(shortToken, longToken);
        }
    }

    @Nested
    @DisplayName("Username Extraction Tests")
    class UsernameExtractionTests {

        private JwtUtil jwtUtil;

        @org.junit.jupiter.api.BeforeEach
        void setUp() {
            jwtUtil = new JwtUtil(VALID_SECRET, false);
        }

        @Test
        @DisplayName("Should extract username from valid token")
        void shouldExtractUsernameFromValidToken() {
            // Arrange
            String token = jwtUtil.generateToken(TEST_USERNAME, ONE_HOUR_MS);

            // Act
            String extractedUsername = jwtUtil.extractUsername(token);

            // Assert
            assertEquals(TEST_USERNAME, extractedUsername);
        }

        @Test
        @DisplayName("Should extract username with special characters")
        void shouldExtractUsernameWithSpecialCharacters() {
            // Arrange
            String specialUsername = "user@email.com";
            String token = jwtUtil.generateToken(specialUsername, ONE_HOUR_MS);

            // Act
            String extractedUsername = jwtUtil.extractUsername(token);

            // Assert
            assertEquals(specialUsername, extractedUsername);
        }

        @Test
        @DisplayName("Should throw exception for invalid token format")
        void shouldThrowExceptionForInvalidTokenFormat() {
            // Act & Assert
            assertThrows(JwtException.class, () -> {
                jwtUtil.extractUsername("invalid.token");
            });
        }

        @Test
        @DisplayName("Should throw exception for tampered token")
        void shouldThrowExceptionForTamperedToken() {
            // Arrange
            String token = jwtUtil.generateToken(TEST_USERNAME, ONE_HOUR_MS);
            String tamperedToken = token.substring(0, token.length() - 5) + "12345";

            // Act & Assert
            assertThrows(JwtException.class, () -> {
                jwtUtil.extractUsername(tamperedToken);
            });
        }
    }

    @Nested
    @DisplayName("Token Validation Tests")
    class TokenValidationTests {

        private JwtUtil jwtUtil;

        @org.junit.jupiter.api.BeforeEach
        void setUp() {
            jwtUtil = new JwtUtil(VALID_SECRET, false);
        }

        @Test
        @DisplayName("Should validate valid token")
        void shouldValidateValidToken() {
            // Arrange
            String token = jwtUtil.generateToken(TEST_USERNAME, ONE_HOUR_MS);

            // Act
            boolean isValid = jwtUtil.validateToken(token);

            // Assert
            assertTrue(isValid);
        }

        @Test
        @DisplayName("Should invalidate expired token")
        void shouldInvalidateExpiredToken() throws InterruptedException {
            // Arrange - create token that expires in 100ms
            String token = jwtUtil.generateToken(TEST_USERNAME, 100L);
            Thread.sleep(150L); // Wait for expiration

            // Act
            boolean isValid = jwtUtil.validateToken(token);

            // Assert
            assertFalse(isValid);
        }

        @Test
        @DisplayName("Should invalidate tampered token")
        void shouldInvalidateTamperedToken() {
            // Arrange
            String token = jwtUtil.generateToken(TEST_USERNAME, ONE_HOUR_MS);
            String tamperedToken = token.substring(0, token.length() - 5) + "ABCDE";

            // Act
            boolean isValid = jwtUtil.validateToken(tamperedToken);

            // Assert
            assertFalse(isValid);
        }

        @Test
        @DisplayName("Should invalidate malformed token")
        void shouldInvalidateMalformedToken() {
            // Act
            boolean isValid = jwtUtil.validateToken("not.a.jwt.token");

            // Assert
            assertFalse(isValid);
        }

        @Test
        @DisplayName("Should invalidate null token")
        void shouldInvalidateNullToken() {
            // Act
            boolean isValid = jwtUtil.validateToken(null);

            // Assert
            assertFalse(isValid);
        }

        @Test
        @DisplayName("Should invalidate empty token")
        void shouldInvalidateEmptyToken() {
            // Act
            boolean isValid = jwtUtil.validateToken("");

            // Assert
            assertFalse(isValid);
        }

        @Test
        @DisplayName("Should invalidate token signed with different key")
        void shouldInvalidateTokenSignedWithDifferentKey() {
            // Arrange
            JwtUtil otherJwtUtil = new JwtUtil("another_very_secure_secret_key_with_at_least_32_characters", false);
            String token = otherJwtUtil.generateToken(TEST_USERNAME, ONE_HOUR_MS);

            // Act
            boolean isValid = jwtUtil.validateToken(token);

            // Assert
            assertFalse(isValid);
        }
    }

    @Nested
    @DisplayName("End-to-End Tests")
    class EndToEndTests {

        @Test
        @DisplayName("Should complete full token lifecycle")
        void shouldCompleteFullTokenLifecycle() {
            // Arrange
            JwtUtil jwtUtil = new JwtUtil(VALID_SECRET, false);

            // Act - Generate token
            String token = jwtUtil.generateToken(TEST_USERNAME, ONE_HOUR_MS);

            // Assert - Token is valid
            assertTrue(jwtUtil.validateToken(token));

            // Assert - Username can be extracted
            String extractedUsername = jwtUtil.extractUsername(token);
            assertEquals(TEST_USERNAME, extractedUsername);
        }

        @Test
        @DisplayName("Should handle multiple users independently")
        void shouldHandleMultipleUsersIndependently() {
            // Arrange
            JwtUtil jwtUtil = new JwtUtil(VALID_SECRET, false);
            String user1 = "user1";
            String user2 = "user2";

            // Act
            String token1 = jwtUtil.generateToken(user1, ONE_HOUR_MS);
            String token2 = jwtUtil.generateToken(user2, ONE_HOUR_MS);

            // Assert
            assertTrue(jwtUtil.validateToken(token1));
            assertTrue(jwtUtil.validateToken(token2));
            assertEquals(user1, jwtUtil.extractUsername(token1));
            assertEquals(user2, jwtUtil.extractUsername(token2));
            assertNotEquals(token1, token2);
        }
    }
}
