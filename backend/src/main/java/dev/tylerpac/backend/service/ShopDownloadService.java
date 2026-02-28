package dev.tylerpac.backend.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dev.tylerpac.backend.model.User;
import dev.tylerpac.backend.repo.ShopOrderRepository;

@Service
public class ShopDownloadService {

    private static final String STATUS_PAID = "PAID";

    private final ShopOrderRepository shopOrderRepository;
    private final Path downloadRoot;

    public ShopDownloadService(
        ShopOrderRepository shopOrderRepository,
        @Value("${app.shop.download-root:downloads}") String downloadRoot
    ) {
        this.shopOrderRepository = shopOrderRepository;
        this.downloadRoot = Paths.get(downloadRoot).toAbsolutePath().normalize();
    }

    public DownloadAsset loadPaidProductAsset(User user, String productId) {
        if (!user.isEmailVerified()) {
            throw new IllegalArgumentException("email_not_verified");
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

    public record DownloadAsset(Resource resource, String fileName, String contentType) {}
}
