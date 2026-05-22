package dev.tylerpac.backend.repo;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.tylerpac.backend.model.DownloadToken;

public interface DownloadTokenRepository extends JpaRepository<DownloadToken, String> {
    Optional<DownloadToken> findByIdAndUsedFalseAndExpiresAtAfter(String id, Instant now);
}
