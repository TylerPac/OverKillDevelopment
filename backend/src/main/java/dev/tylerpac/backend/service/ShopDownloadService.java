package dev.tylerpac.backend.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import dev.tylerpac.backend.model.DownloadToken;
import dev.tylerpac.backend.model.User;
import dev.tylerpac.backend.repo.DownloadTokenRepository;
import dev.tylerpac.backend.repo.ShopOrderRepository;

@Service
public class ShopDownloadService {

    private static final String STATUS_PAID = "PAID";
    private static final long TOKEN_TTL_SECONDS = 600;

    private final ShopOrderRepository shopOrderRepository;
    private final DownloadTokenRepository downloadTokenRepository;
    private final Path downloadRoot;
    private final Environment env;

    public ShopDownloadService(
        ShopOrderRepository shopOrderRepository,
        DownloadTokenRepository downloadTokenRepository,
        @Value("${app.shop.download-root:downloads}") String downloadRoot,
        Environment env
    ) {
        this.shopOrderRepository = shopOrderRepository;
        this.downloadTokenRepository = downloadTokenRepository;
        this.downloadRoot = Paths.get(downloadRoot).toAbsolutePath().normalize();
        this.env = env;
    }

    public Optional<String> findDownloadLink(User user, String productId) {
        if (!StringUtils.hasText(user.getSteam64Id())) {
            throw new IllegalArgumentException("account_not_setup");
        }

        if (!StringUtils.hasText(productId)) {
            throw new IllegalArgumentException("invalid_product");
        }

        boolean purchased = shopOrderRepository.existsByUserAndProductIdAndStatusIgnoreCase(user, productId, STATUS_PAID);
        if (!purchased) {
            throw new IllegalArgumentException("purchase_required");
        }

        String link = env.getProperty("app.shop.download-link." + productId);
        return Optional.ofNullable(link).filter(StringUtils::hasText);
    }

    public DownloadAsset loadPaidProductAsset(User user, String productId) {
        if (!StringUtils.hasText(user.getSteam64Id())) {
            throw new IllegalArgumentException("account_not_setup");
        }

        if (!StringUtils.hasText(productId)) {
            throw new IllegalArgumentException("invalid_product");
        }

        boolean purchased = shopOrderRepository.existsByUserAndProductIdAndStatusIgnoreCase(user, productId, STATUS_PAID);
        if (!purchased) {
            throw new IllegalArgumentException("purchase_required");
        }

        Path productFile = downloadRoot.resolve(productId + ".zip").normalize();
        if (!productFile.startsWith(downloadRoot)) {
            throw new IllegalArgumentException("invalid_product");
        }

        if (!Files.exists(productFile) || !Files.isRegularFile(productFile)) {
            throw new IllegalArgumentException("download_not_found");
        }

        try {
            Resource resource = new UrlResource(productFile.toUri());
            String contentType = Files.probeContentType(productFile);
            return new DownloadAsset(resource, productFile.getFileName().toString(), contentType);
        } catch (IOException ex) {
            throw new IllegalStateException("download_unavailable", ex);
        }
    }

    @Transactional
    public String issueDownloadToken(User user, String productId) {
        if (!StringUtils.hasText(user.getSteam64Id())) {
            throw new IllegalArgumentException("account_not_setup");
        }

        if (!StringUtils.hasText(productId)) {
            throw new IllegalArgumentException("invalid_product");
        }

        boolean purchased = shopOrderRepository.existsByUserAndProductIdAndStatusIgnoreCase(user, productId, STATUS_PAID);
        if (!purchased) {
            throw new IllegalArgumentException("purchase_required");
        }

        Path productFile = downloadRoot.resolve(productId + ".zip").normalize();
        if (!productFile.startsWith(downloadRoot)) {
            throw new IllegalArgumentException("invalid_product");
        }

        if (!Files.exists(productFile) || !Files.isRegularFile(productFile)) {
            throw new IllegalArgumentException("download_not_found");
        }

        DownloadToken token = new DownloadToken();
        token.setId(UUID.randomUUID().toString());
        token.setUser(user);
        token.setProductId(productId);
        token.setExpiresAt(Instant.now().plusSeconds(TOKEN_TTL_SECONDS));
        downloadTokenRepository.save(token);
        return token.getId();
    }

    @Transactional
    public DownloadAsset consumeDownloadToken(String tokenId) {
        DownloadToken token = downloadTokenRepository
            .findByIdAndUsedFalseAndExpiresAtAfter(tokenId, Instant.now())
            .orElseThrow(() -> new IllegalArgumentException("invalid_token"));

        token.setUsed(true);
        downloadTokenRepository.save(token);

        return loadAsset(token.getProductId());
    }

    private DownloadAsset loadAsset(String productId) {
        Path productFile = downloadRoot.resolve(productId + ".zip").normalize();
        if (!productFile.startsWith(downloadRoot)) {
            throw new IllegalArgumentException("invalid_product");
        }

        if (!Files.exists(productFile) || !Files.isRegularFile(productFile)) {
            throw new IllegalArgumentException("download_not_found");
        }

        try {
            Resource resource = new UrlResource(productFile.toUri());
            String contentType = Files.probeContentType(productFile);
            return new DownloadAsset(resource, productFile.getFileName().toString(), contentType);
        } catch (IOException ex) {
            throw new IllegalStateException("download_unavailable", ex);
        }
    }

    public record DownloadAsset(Resource resource, String fileName, String contentType) {}
}
