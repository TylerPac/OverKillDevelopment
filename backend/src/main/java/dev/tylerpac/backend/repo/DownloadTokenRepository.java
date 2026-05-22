package dev.tylerpac.backend.repo;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.tylerpac.backend.model.DownloadToken;
import dev.tylerpac.backend.model.User;

public interface DownloadTokenRepository extends JpaRepository<DownloadToken, String> {
    Optional<DownloadToken> findByIdAndUsedFalseAndExpiresAtAfter(String id, Instant now);
    Optional<DownloadToken> findTopByUserAndProductIdAndUsedTrueOrderByExpiresAtDesc(User user, String productId);
}
