package dev.tylerpac.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.tylerpac.backend.model.AuthLoginLock;
import dev.tylerpac.backend.model.AuthRateLimitBucket;
import dev.tylerpac.backend.repo.AuthLoginLockRepository;
import dev.tylerpac.backend.repo.AuthRateLimitBucketRepository;

/**
 * Unit tests for AuthSecurityService - Authentication security, rate limiting, and brute force protection
 */
@ExtendWith(MockitoExtension.class)
class AuthSecurityServiceTest {

    @Mock
    private AuthRateLimitBucketRepository authRateLimitBucketRepository;

    @Mock
    private AuthLoginLockRepository authLoginLockRepository;

    private AuthSecurityService authSecurityService;

    private static final String IP_ADDRESS = "192.168.1.1";
    private static final String USERNAME = "testuser";
    private static final int MAX_REQUESTS_PER_MINUTE = 60;
    private static final int MAX_FAILURES = 5;
    private static final long LOCK_MINUTES = 15;

    @BeforeEach
    void setUp() {
        authSecurityService = new AuthSecurityService(
            authRateLimitBucketRepository,
            authLoginLockRepository,
            MAX_REQUESTS_PER_MINUTE,
            MAX_FAILURES,
            LOCK_MINUTES
        );
    }

    @Nested
    @DisplayName("IP Rate Limiting Tests")
    class IpRateLimitingTests {

        @Test
        @DisplayName("Should not rate limit when requests are within limit")
        void shouldNotRateLimitWithinLimit() {
            // Arrange
            Instant now = Instant.now();
            AuthRateLimitBucket bucket = new AuthRateLimitBucket();
            bucket.setIpAddress(IP_ADDRESS);
            bucket.setWindowStart(now);
            bucket.setRequestCount(30); // Half of max
            
            when(authRateLimitBucketRepository.findByIpAddress(IP_ADDRESS))
                .thenReturn(Optional.of(bucket));

            // Act
            boolean isLimited = authSecurityService.isIpRateLimited(IP_ADDRESS);

            // Assert
            assertFalse(isLimited);
            verify(authRateLimitBucketRepository).save(argThat(b -> b.getRequestCount() == 31));
        }

        @Test
        @DisplayName("Should rate limit when requests exceed limit")
        void shouldRateLimitWhenExceedingLimit() {
            // Arrange
            Instant now = Instant.now();
            AuthRateLimitBucket bucket = new AuthRateLimitBucket();
            bucket.setIpAddress(IP_ADDRESS);
            bucket.setWindowStart(now);
            bucket.setRequestCount(MAX_REQUESTS_PER_MINUTE); // At max
            
            when(authRateLimitBucketRepository.findByIpAddress(IP_ADDRESS))
                .thenReturn(Optional.of(bucket));

            // Act
            boolean isLimited = authSecurityService.isIpRateLimited(IP_ADDRESS);

            // Assert
            assertTrue(isLimited);
        }

        @Test
        @DisplayName("Should create new bucket for first request from IP")
        void shouldCreateNewBucketForNewIp() {
            // Arrange
            when(authRateLimitBucketRepository.findByIpAddress(IP_ADDRESS))
                .thenReturn(Optional.empty());

            // Act
            boolean isLimited = authSecurityService.isIpRateLimited(IP_ADDRESS);

            // Assert
            assertFalse(isLimited);
            ArgumentCaptor<AuthRateLimitBucket> captor = ArgumentCaptor.forClass(AuthRateLimitBucket.class);
            verify(authRateLimitBucketRepository).save(captor.capture());
            
            AuthRateLimitBucket saved = captor.getValue();
            assertEquals(IP_ADDRESS, saved.getIpAddress());
            assertEquals(1, saved.getRequestCount());
        }

        @Test
        @DisplayName("Should reset window after minute expires")
        void shouldResetWindowAfterExpiration() {
            // Arrange
            Instant oldTime = Instant.now().minus(Duration.ofMinutes(2));
            AuthRateLimitBucket bucket = new AuthRateLimitBucket();
            bucket.setIpAddress(IP_ADDRESS);
            bucket.setWindowStart(oldTime);
            bucket.setRequestCount(MAX_REQUESTS_PER_MINUTE + 10); // Over limit
            
            when(authRateLimitBucketRepository.findByIpAddress(IP_ADDRESS))
                .thenReturn(Optional.of(bucket));

            // Act
            boolean isLimited = authSecurityService.isIpRateLimited(IP_ADDRESS);

            // Assert
            assertFalse(isLimited); // Should not be limited after window reset
            verify(authRateLimitBucketRepository).save(argThat(b -> b.getRequestCount() == 1));
        }
    }

    @Nested
    @DisplayName("Credential Locking Tests")
    class CredentialLockingTests {

        @Test
        @DisplayName("Should not be locked when no lock exists")
        void shouldNotBeLockedWhenNoLockExists() {
            // Arrange
            when(authLoginLockRepository.findByLockKey(anyString()))
                .thenReturn(Optional.empty());

            // Act
            boolean isLocked = authSecurityService.isCredentialLocked(USERNAME, IP_ADDRESS);

            // Assert
            assertFalse(isLocked);
        }

        @Test
        @DisplayName("Should not be locked when lockedUntil is null")
        void shouldNotBeLockedWhenLockedUntilIsNull() {
            // Arrange
            AuthLoginLock lock = new AuthLoginLock();
            lock.setLockKey(USERNAME + "|" + IP_ADDRESS);
            lock.setLockedUntil(null);
            
            when(authLoginLockRepository.findByLockKey(anyString()))
                .thenReturn(Optional.of(lock));

            // Act
            boolean isLocked = authSecurityService.isCredentialLocked(USERNAME, IP_ADDRESS);

            // Assert
            assertFalse(isLocked);
        }

        @Test
        @DisplayName("Should be locked when lock is still active")
        void shouldBeLockedWhenLockIsActive() {
            // Arrange
            Instant lockedUntil = Instant.now().plus(Duration.ofMinutes(10));
            AuthLoginLock lock = new AuthLoginLock();
            lock.setLockKey(USERNAME + "|" + IP_ADDRESS);
            lock.setLockedUntil(lockedUntil);
            lock.setFailureCount(5);
            lock.setWindowStart(Instant.now());
            
            when(authLoginLockRepository.findByLockKey(anyString()))
                .thenReturn(Optional.of(lock));

            // Act
            boolean isLocked = authSecurityService.isCredentialLocked(USERNAME, IP_ADDRESS);

            // Assert
            assertTrue(isLocked);
        }

        @Test
        @DisplayName("Should unlock and reset when lock expires")
        void shouldUnlockWhenLockExpires() {
            // Arrange
            Instant expiredLock = Instant.now().minus(Duration.ofMinutes(1));
            AuthLoginLock lock = new AuthLoginLock();
            lock.setLockKey(USERNAME + "|" + IP_ADDRESS);
            lock.setLockedUntil(expiredLock);
            lock.setFailureCount(5);
            lock.setWindowStart(Instant.now().minus(Duration.ofMinutes(20)));
            
            when(authLoginLockRepository.findByLockKey(anyString()))
                .thenReturn(Optional.of(lock));

            // Act
            boolean isLocked = authSecurityService.isCredentialLocked(USERNAME, IP_ADDRESS);

            // Assert
            assertFalse(isLocked);
            verify(authLoginLockRepository).save(argThat(l -> 
                l.getLockedUntil() == null && l.getFailureCount() == 0
            ));
        }
    }

    @Nested
    @DisplayName("Auth Failure Recording Tests")
    class AuthFailureRecordingTests {

        @Test
        @DisplayName("Should create new lock record on first failure")
        void shouldCreateNewLockOnFirstFailure() {
            // Arrange
            when(authLoginLockRepository.findByLockKey(anyString()))
                .thenReturn(Optional.empty());

            // Act
            authSecurityService.recordAuthFailure(USERNAME, IP_ADDRESS);

            // Assert
            ArgumentCaptor<AuthLoginLock> captor = ArgumentCaptor.forClass(AuthLoginLock.class);
            verify(authLoginLockRepository).save(captor.capture());
            
            AuthLoginLock saved = captor.getValue();
            assertEquals(USERNAME + "|" + IP_ADDRESS, saved.getLockKey());
            assertEquals(1, saved.getFailureCount());
            assertNull(saved.getLockedUntil());
        }

        @Test
        @DisplayName("Should increment failure count on subsequent failures")
        void shouldIncrementFailureCount() {
            // Arrange
            Instant now = Instant.now();
            AuthLoginLock lock = new AuthLoginLock();
            lock.setLockKey(USERNAME + "|" + IP_ADDRESS);
            lock.setWindowStart(now);
            lock.setFailureCount(2);
            lock.setLockedUntil(null);
            
            when(authLoginLockRepository.findByLockKey(anyString()))
                .thenReturn(Optional.of(lock));

            // Act
            authSecurityService.recordAuthFailure(USERNAME, IP_ADDRESS);

            // Assert
            verify(authLoginLockRepository).save(argThat(l -> l.getFailureCount() == 3));
        }

        @Test
        @DisplayName("Should lock account after max failures")
        void shouldLockAccountAfterMaxFailures() {
            // Arrange
            Instant now = Instant.now();
            AuthLoginLock lock = new AuthLoginLock();
            lock.setLockKey(USERNAME + "|" + IP_ADDRESS);
            lock.setWindowStart(now);
            lock.setFailureCount(MAX_FAILURES - 1); // One away from lock
            lock.setLockedUntil(null);
            
            when(authLoginLockRepository.findByLockKey(anyString()))
                .thenReturn(Optional.of(lock));

            // Act
            authSecurityService.recordAuthFailure(USERNAME, IP_ADDRESS);

            // Assert
            verify(authLoginLockRepository).save(argThat(l -> 
                l.getLockedUntil() != null && 
                l.getFailureCount() == 0 // Reset after lock
            ));
        }

        @Test
        @DisplayName("Should reset window after expiration")
        void shouldResetWindowAfterExpiration() {
            // Arrange
            Instant oldTime = Instant.now().minus(Duration.ofMinutes(20));
            AuthLoginLock lock = new AuthLoginLock();
            lock.setLockKey(USERNAME + "|" + IP_ADDRESS);
            lock.setWindowStart(oldTime);
            lock.setFailureCount(3);
            lock.setLockedUntil(null);
            
            when(authLoginLockRepository.findByLockKey(anyString()))
                .thenReturn(Optional.of(lock));

            // Act
            authSecurityService.recordAuthFailure(USERNAME, IP_ADDRESS);

            // Assert
            verify(authLoginLockRepository).save(argThat(l -> 
                l.getFailureCount() == 1 && // Reset and then incremented
                l.getLockedUntil() == null
            ));
        }

        @Test
        @DisplayName("Should not record failure when already locked")
        void shouldNotRecordFailureWhenAlreadyLocked() {
            // Arrange
            Instant lockedUntil = Instant.now().plus(Duration.ofMinutes(10));
            AuthLoginLock lock = new AuthLoginLock();
            lock.setLockKey(USERNAME + "|" + IP_ADDRESS);
            lock.setWindowStart(Instant.now());
            lock.setFailureCount(0);
            lock.setLockedUntil(lockedUntil);
            
            when(authLoginLockRepository.findByLockKey(anyString()))
                .thenReturn(Optional.of(lock));

            // Act
            authSecurityService.recordAuthFailure(USERNAME, IP_ADDRESS);

            // Assert
            verify(authLoginLockRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Auth Success Recording Tests")
    class AuthSuccessRecordingTests {

        @Test
        @DisplayName("Should delete lock on successful authentication")
        void shouldDeleteLockOnAuthSuccess() {
            // Act
            authSecurityService.recordAuthSuccess(USERNAME, IP_ADDRESS);

            // Assert
            verify(authLoginLockRepository).deleteByLockKey(USERNAME + "|" + IP_ADDRESS);
        }

        @Test
        @DisplayName("Should use correct composite key format")
        void shouldUseCorrectCompositeKeyFormat() {
            // Act
            authSecurityService.recordAuthSuccess("user123", "10.0.0.1");

            // Assert
            verify(authLoginLockRepository).deleteByLockKey("user123|10.0.0.1");
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle empty username")
        void shouldHandleEmptyUsername() {
            // Act
            authSecurityService.recordAuthSuccess("", IP_ADDRESS);

            // Assert
            verify(authLoginLockRepository).deleteByLockKey("|" + IP_ADDRESS);
        }

        @Test
        @DisplayName("Should handle empty IP address")
        void shouldHandleEmptyIpAddress() {
            // Act
            authSecurityService.recordAuthSuccess(USERNAME, "");

            // Assert
            verify(authLoginLockRepository).deleteByLockKey(USERNAME + "|");
        }

        @Test
        @DisplayName("Should handle special characters in username")
        void shouldHandleSpecialCharactersInUsername() {
            // Arrange
            String specialUsername = "user@example.com";
            when(authLoginLockRepository.findByLockKey(anyString()))
                .thenReturn(Optional.empty());

            // Act
            authSecurityService.recordAuthFailure(specialUsername, IP_ADDRESS);

            // Assert
            verify(authLoginLockRepository).save(argThat(l -> 
                l.getLockKey().equals(specialUsername + "|" + IP_ADDRESS)
            ));
        }
    }
}
