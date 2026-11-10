package dev.tylerpac.backend.downloads;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.tylerpac.backend.dto.ShopProductResponse;
import dev.tylerpac.backend.model.User;
import dev.tylerpac.backend.repo.UserRepository;
import dev.tylerpac.backend.security.AdminAccess;
import dev.tylerpac.backend.service.StripeShopService;
import tools.jackson.databind.JsonNode;

// The admin page's Downloads section. Shared files (Core, API, each mod) are uploaded once; each product says which
// files its single download contains. Every call checks the admin with the shared login service first.
// Uploads go from the browser straight to downloads.tylerpac.dev with the token returned by startUpload.
@RestController
@RequestMapping("/shop/admin/downloads")
public class AdminDownloadsController {

    private static final Pattern KEY = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
    private static final Pattern GITHUB = Pattern.compile("https://github\\.com/[A-Za-z0-9_.-]+(/[A-Za-z0-9_.-]+)?/?");

    public record SaveFile(String displayName, List<String> parts) {
    }

    public record StartUpload(String fileName, Long sizeBytes, String sha256, String note) {
    }

    public record SetCurrent(Long versionId) {
    }

    public record SaveProduct(List<String> files, String fileName, String githubUrl) {
    }

    private final DownloadServiceClient downloads;
    private final ProductLinkRepository links;
    private final StripeShopService shop;
    private final UserRepository users;
    private final AdminAccess adminAccess;

    public AdminDownloadsController(DownloadServiceClient downloads, ProductLinkRepository links, StripeShopService shop,
                                    UserRepository users, AdminAccess adminAccess) {
        this.downloads = downloads;
        this.links = links;
        this.shop = shop;
        this.users = users;
        this.adminAccess = adminAccess;
    }

    /** Everything the section shows. Products and links still load when the download service is unreachable. */
    @GetMapping
    public Map<String, Object> overview(Principal principal) {
        requireAdmin(principal);
        Map<String, Object> body = new LinkedHashMap<>();
        List<Map<String, Object>> products = new ArrayList<>();
        for (ShopProductResponse product : shop.getProducts()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", product.getId());
            row.put("name", product.getName());
            row.put("githubUrl", links.findById(product.getId()).map(ProductLink::getGithubUrl).orElse(null));
            products.add(row);
        }
        body.put("products", products);
        body.put("configured", downloads.isConfigured());
        try {
            body.put("files", downloads.artifacts());
            body.put("bundles", downloads.bundles());
        } catch (DownloadServiceException e) {
            body.put("files", List.of());
            body.put("bundles", List.of());
            body.put("error", e.code());
        }
        return body;
    }

    /** Creates (or renames) a file's client and/or server part. Each part is uploaded and versioned on its own. */
    @PutMapping("/files/{key}")
    public List<JsonNode> saveFile(Principal principal, @PathVariable String key, @RequestBody SaveFile body) {
        requireAdmin(principal);
        requireKey(key);
        if (DownloadParts.endsWithPart(key)) {
            throw new DownloadServiceException(400, "invalid_key");
        }
        List<String> parts = body.parts() == null ? List.of() : body.parts().stream().distinct().toList();
        if (parts.isEmpty() || !parts.stream().allMatch(DownloadParts::isPart)) {
            throw new DownloadServiceException(400, "invalid_parts");
        }
        String name = body.displayName() == null ? "" : body.displayName().trim();
        return parts.stream()
            .map(part -> downloads.saveArtifact(DownloadParts.key(key, part), name + " (" + DownloadParts.label(part) + ")"))
            .toList();
    }

    /** Deletes one part (for example "core-server") with all its versions. Refused while a product includes it. */
    @DeleteMapping("/files/{key}")
    public ResponseEntity<Void> deleteFile(Principal principal, @PathVariable String key) {
        requireAdmin(principal);
        downloads.deleteArtifact(requireKey(key));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/files/{key}/uploads")
    public JsonNode startUpload(Principal principal, @PathVariable String key, @RequestBody StartUpload body) {
        String admin = requireAdmin(principal);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("fileName", body.fileName());
        request.put("sizeBytes", body.sizeBytes());
        if (StringUtils.hasText(body.sha256())) {
            request.put("sha256", body.sha256());
        }
        if (StringUtils.hasText(body.note())) {
            request.put("note", body.note());
        }
        // Recorded with the version, so the history shows who uploaded it
        request.put("uploadedBy", admin);
        return downloads.startUpload(requireKey(key), request);
    }

    @GetMapping("/uploads/{uploadId}")
    public JsonNode upload(Principal principal, @PathVariable String uploadId) {
        requireAdmin(principal);
        if (!uploadId.matches("[0-9a-f]{32}")) {
            throw new DownloadServiceException(400, "invalid_upload_id");
        }
        return downloads.upload(uploadId);
    }

    @PostMapping("/files/{key}/current")
    public JsonNode setCurrent(Principal principal, @PathVariable String key, @RequestBody SetCurrent body) {
        requireAdmin(principal);
        if (body.versionId() == null) {
            throw new DownloadServiceException(400, "invalid_version_id");
        }
        return downloads.setCurrent(requireKey(key), body.versionId());
    }

    /** Which files make up a product's download, its file name, and its GitHub link. */
    @PutMapping("/products/{productId}")
    public Map<String, Object> saveProduct(Principal principal, @PathVariable String productId, @RequestBody SaveProduct body) {
        requireAdmin(principal);
        ShopProductResponse product = shop.getProducts().stream()
            .filter(p -> p.getId().equals(productId))
            .findFirst()
            .orElseThrow(() -> new DownloadServiceException(404, "product_not_found"));

        String githubUrl = body.githubUrl() == null ? "" : body.githubUrl().trim();
        if (!githubUrl.isEmpty() && !GITHUB.matcher(githubUrl).matches()) {
            throw new DownloadServiceException(400, "invalid_github_url");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        if (body.files() != null && !body.files().isEmpty()) {
            body.files().forEach(this::requireKey);
            requireKey(productId);
            Set<String> existing = new HashSet<>();
            downloads.artifacts().forEach(artifact -> existing.add(artifact.path("key").asString("")));
            Set<String> bundleKeys = new HashSet<>();
            downloads.bundles().forEach(bundle -> bundleKeys.add(bundle.path("key").asString("")));

            // One download per part, made of that part of each chosen file (a file without that part is left out)
            String baseName = StringUtils.hasText(body.fileName()) ? body.fileName().trim().replaceFirst("(?i)\\.zip$", "") : productId;
            List<JsonNode> saved = new ArrayList<>();
            for (String part : DownloadParts.ALL) {
                List<String> members = body.files().stream()
                    .map(file -> DownloadParts.key(file, part))
                    .filter(existing::contains)
                    .toList();
                String bundleKey = DownloadParts.key(productId, part);
                if (!members.isEmpty()) {
                    Map<String, Object> bundle = new LinkedHashMap<>();
                    bundle.put("displayName", product.getName() + " (" + DownloadParts.label(part) + ")");
                    bundle.put("fileName", baseName + "-" + DownloadParts.label(part) + ".zip");
                    bundle.put("artifacts", members);
                    saved.add(downloads.saveBundle(bundleKey, bundle));
                } else if (bundleKeys.contains(bundleKey)) {
                    downloads.deleteBundle(bundleKey);
                }
            }
            if (saved.isEmpty()) {
                throw new DownloadServiceException(400, "no_parts");
            }
            // A single download from before files had client and server parts
            if (bundleKeys.contains(productId)) {
                downloads.deleteBundle(productId);
            }
            result.put("bundles", saved);
        }

        if (githubUrl.isEmpty()) {
            links.deleteById(productId);
        } else {
            ProductLink link = links.findById(productId).orElseGet(() -> new ProductLink(productId, githubUrl));
            link.update(githubUrl);
            links.save(link);
        }
        result.put("githubUrl", githubUrl.isEmpty() ? null : githubUrl);
        return result;
    }

    // ---- errors ---------------------------------------------------------------------------------------

    @ExceptionHandler(DownloadServiceException.class)
    ResponseEntity<Map<String, String>> downloadsError(DownloadServiceException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.code()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> refused(IllegalArgumentException e) {
        HttpStatus status = "unauthorized".equals(e.getMessage()) ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN;
        return ResponseEntity.status(status).body(Map.of("error", status == HttpStatus.UNAUTHORIZED ? "unauthorized" : "forbidden"));
    }

    // ---- helpers --------------------------------------------------------------------------------------

    /** The admin's Steam id; 401 when signed out, 403 when not an admin, 503 when the login service is down. */
    private String requireAdmin(Principal principal) {
        if (principal == null || !StringUtils.hasText(principal.getName())) {
            throw new IllegalArgumentException("unauthorized");
        }
        User user = users.findByUsername(principal.getName()).orElseThrow(() -> new IllegalArgumentException("unauthorized"));
        adminAccess.requireAdmin(user.getSteam64Id());
        return user.getSteam64Id();
    }

    private String requireKey(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new DownloadServiceException(400, "invalid_key");
        }
        return key;
    }
}
