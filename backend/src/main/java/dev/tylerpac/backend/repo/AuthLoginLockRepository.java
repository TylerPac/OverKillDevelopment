package dev.tylerpac.backend.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import dev.tylerpac.backend.model.AuthLoginLock;
import jakarta.persistence.LockModeType;

public interface AuthLoginLockRepository extends JpaRepository<AuthLoginLock, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthLoginLock> findByLockKey(String lockKey);

    void deleteByLockKey(String lockKey);
}
