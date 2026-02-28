package dev.tylerpac.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.tylerpac.backend.model.AuthLoginLock;
import dev.tylerpac.backend.model.AuthRateLimitBucket;
import dev.tylerpac.backend.repo.AuthLoginLockRepository;
import dev.tylerpac.backend.repo.AuthRateLimitBucketRepository;

@Service
public class AuthSecurityService {

    private final int maxRequestsPerMinute;
    private final int maxFailuresPerWindow;
    private final Duration lockDuration;
    private final Duration failureWindowDuration = Duration.ofMinutes(15);

    private final AuthRateLimitBucketRepository authRateLimitBucketRepository;
    private final AuthLoginLockRepository authLoginLockRepository;

    public AuthSecurityService(
        AuthRateLimitBucketRepository authRateLimitBucketRepository,
        AuthLoginLockRepository authLoginLockRepository,
        @Value("${app.auth.rate-limit.max-requests-per-minute:60}") int maxRequestsPerMinute,
        @Value("${app.auth.bruteforce.max-failures:5}") int maxFailuresPerWindow,
        @Value("${app.auth.bruteforce.lock-minutes:15}") long lockMinutes
    ) {
        this.authRateLimitBucketRepository = authRateLimitBucketRepository;
        this.authLoginLockRepository = authLoginLockRepository;
        this.maxRequestsPerMinute = maxRequestsPerMinute;
        this.maxFailuresPerWindow = maxFailuresPerWindow;
        this.lockDuration = Duration.ofMinutes(lockMinutes);
    }

    @Transactional
    public boolean isIpRateLimited(String ipAddress) {
        Instant now = Instant.now();
        Optional<AuthRateLimitBucket> bucketOpt = authRateLimitBucketRepository.findByIpAddress(ipAddress);

        AuthRateLimitBucket bucket = bucketOpt.orElseGet(() -> {
            AuthRateLimitBucket created = new AuthRateLimitBucket();
            created.setIpAddress(ipAddress);
            created.setWindowStart(now);
            created.setRequestCount(0);
            return created;
        });

        if (now.isAfter(bucket.getWindowStart().plus(Duration.ofMinutes(1)))) {
            bucket.setWindowStart(now);
            bucket.setRequestCount(0);
        }

        bucket.setRequestCount(bucket.getRequestCount() + 1);
        try {
            authRateLimitBucketRepository.save(bucket);
        } catch (DataIntegrityViolationException ex) {
            return isIpRateLimited(ipAddress);
        }

        return bucket.getRequestCount() > maxRequestsPerMinute;
    }

    @Transactional
    public boolean isCredentialLocked(String username, String ipAddress) {
        String lockKey = compositeKey(username, ipAddress);
        Optional<AuthLoginLock> lockOpt = authLoginLockRepository.findByLockKey(lockKey);
        if (lockOpt.isEmpty()) {
            return false;
        }

        AuthLoginLock lock = lockOpt.get();
        if (lock.getLockedUntil() == null) {
            return false;
        }

        Instant now = Instant.now();
        if (now.isAfter(lock.getLockedUntil())) {
            lock.setLockedUntil(null);
            lock.setFailureCount(0);
            lock.setWindowStart(now);
            authLoginLockRepository.save(lock);
            return false;
        }

        return true;
    }

    @Transactional
    public void recordAuthFailure(String username, String ipAddress) {
        Instant now = Instant.now();
        String lockKey = compositeKey(username, ipAddress);
        Optional<AuthLoginLock> lockOpt = authLoginLockRepository.findByLockKey(lockKey);

        AuthLoginLock lock = lockOpt.orElseGet(() -> {
            AuthLoginLock created = new AuthLoginLock();
            created.setLockKey(lockKey);
            created.setWindowStart(now);
            created.setFailureCount(0);
            created.setLockedUntil(null);
            return created;
        });

        if (lock.getLockedUntil() != null && now.isBefore(lock.getLockedUntil())) {
            return;
        }

        if (now.isAfter(lock.getWindowStart().plus(failureWindowDuration))) {
            lock.setWindowStart(now);
            lock.setFailureCount(0);
            lock.setLockedUntil(null);
        }

        int failures = lock.getFailureCount() + 1;
        lock.setFailureCount(failures);
        if (failures >= maxFailuresPerWindow) {
            lock.setLockedUntil(now.plus(lockDuration));
            lock.setFailureCount(0);
        }

        try {
            authLoginLockRepository.save(lock);
        } catch (DataIntegrityViolationException ex) {
            recordAuthFailure(username, ipAddress);
        }
    }

    @Transactional
    public void recordAuthSuccess(String username, String ipAddress) {
        authLoginLockRepository.deleteByLockKey(compositeKey(username, ipAddress));
    }

    private String compositeKey(String username, String ipAddress) {
        return username + "|" + ipAddress;
    }
}
