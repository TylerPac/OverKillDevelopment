package dev.tylerpac.backend.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.tylerpac.backend.model.AuthRateLimitBucket;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface AuthRateLimitBucketRepository extends JpaRepository<AuthRateLimitBucket, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthRateLimitBucket> findByIpAddress(String ipAddress);
}
