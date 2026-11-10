package dev.tylerpac.backend.downloads;

import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

// The admin page's Downloads section. A file group (Core, API, each mod) has a client and/or a server part, and each
// part holds any number of uploaded files, placed in the standard mod layout under the group's mod folder. Each
// product has a client and a server download made of those parts. Every call checks the admin with the shared login
// service first. Uploads go from the browser straight to downloads.tylerpac.dev with the token from startUpload.
@RestController
@RequestMapping("/shop/admin/downloads")
public class AdminDownloadsController {

    private static final Pattern KEY = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
    private static final Pattern GITHUB = Pattern.compile("https://github\\.com/[A-Za-z0-9_.-]+(/[A-Za-z0-9_.-]+)?/?");
    private static final Pattern MOD_FOLDER = Pattern.compile("@?[A-Za-z0-9_.-]{1,99}");
    private static final Pattern FILE_NAME = Pattern.compile("[A-Za-z0-9@][A-Za-z0-9@._ ()+-]{0,199}");

    public record SaveFile(String displayName, List<String> parts, String modFolder) {
    }

    public record AddEntry(String fileName) {
    }

    public record StartUpload(String fileName, Long sizeBytes, String sha256, String note) {
    }

    public record SetCurrent(Long versionId) {
    }

    public record SaveProduct(List<String> files, String fileName, String githubUrl) {
    }

    private final DownloadServiceClient downloads;
    private final ProductLinkRepository links;
    private final DownloadGroupRepository groups;
    private final StripeShopService shop;
    private final UserRepository users;
    private final AdminAccess adminAccess;

    public AdminDownloadsController(DownloadServiceClient downloads, ProductLinkRepository links, DownloadGroupRepository groups,
                                    StripeShopService shop, UserRepository users, AdminAccess adminAccess) {
        this.downloads = downloads;
        this.links = links;
        this.groups = groups;
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
        Map<String, String> folders = new LinkedHashMap<>();
        groups.findAll().forEach(group -> folders.put(group.getBaseKey(), group.getModFolder()));
        body.put("modFolders", folders);
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

    /**
     * Creates a file group's parts, or renames it and changes its mod folder. Changing the folder moves every file of
     * the group, which rebuilds the downloads that include it.
     */
    @PutMapping("/files/{key}")
    public Map<String, Object> saveFile(Principal principal, @PathVariable String key, @RequestBody SaveFile body) {
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
        String folder = StringUtils.hasText(body.modFolder()) ? body.modFolder().trim() : DownloadParts.defaultModFolder(name);
        if (!MOD_FOLDER.matcher(folder).matches()) {
            throw new DownloadServiceException(400, "invalid_mod_folder");
        }
        if (!folder.startsWith("@")) {
            folder = "@" + folder;
        }

        // A group seen for the first time also places files uploaded before it had a folder
        boolean firstTime = groups.findById(key).isEmpty();
        DownloadGroup group = groups.findById(key).orElseGet(() -> new DownloadGroup(key, DownloadParts.defaultModFolder(name)));
        boolean moved = firstTime || !folder.equals(group.getModFolder());
        group.setModFolder(folder);
        groups.save(group);

        for (String part : parts) {
            downloads.saveArtifact(DownloadParts.key(key, part), name + " (" + DownloadParts.label(part) + ")", null);
        }
        if (moved) {
            placeFiles(key, folder);
        }
        return Map.of("key", key, "modFolder", folder);
    }

    /** Adds (or returns) the entry for one uploaded file in a part; uploading the same file name again makes a new version. */
    @PostMapping("/files/{key}/{part}/entries")
    public Map<String, Object> addEntry(Principal principal, @PathVariable String key, @PathVariable String part,
                                        @RequestBody AddEntry body) {
        requireAdmin(principal);
        requireKey(key);
        if (!DownloadParts.isPart(part) || DownloadParts.endsWithPart(key)) {
            throw new DownloadServiceException(400, "invalid_parts");
        }
        String fileName = body.fileName() == null ? "" : body.fileName().trim();
        if (!FILE_NAME.matcher(fileName).matches() || fileName.contains("..")) {
            throw new DownloadServiceException(400, "invalid_file_name");
        }
        String entryKey = DownloadParts.entryKey(key, part, fileName);
        String folder = groups.findById(key).map(DownloadGroup::getModFolder).orElse(null);
        downloads.saveArtifact(entryKey, fileName, DownloadParts.folderFor(folder, fileName));
        return Map.of("key", entryKey);
    }

    /** After uploads finish: puts the group's current files into every product download that includes it. */
    @PostMapping("/files/{key}/refresh")
    public Map<String, Object> refresh(Principal principal, @PathVariable String key) {
        requireAdmin(principal);
        return Map.of("bundles", refreshProducts(requireKey(key), null));
    }

    /**
     * Deletes one uploaded file (or a whole old single file) with all its versions. Product downloads that include
     * it are rebuilt without it first.
     */
    @DeleteMapping("/files/{key}")
    public ResponseEntity<Void> deleteFile(Principal principal, @PathVariable String key) {
        requireAdmin(principal);
        requireKey(key);
        DownloadParts.Parsed parsed = DownloadParts.parse(key);
        if (parsed != null) {
            refreshProducts(parsed.base(), key);
        }
        downloads.deleteArtifact(key);
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

    /** Which file groups make up a product's client and server downloads, their file names, and its GitHub link. */
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
            JsonNode artifacts = downloads.artifacts();
            Set<String> bundleKeys = new LinkedHashSet<>();
            downloads.bundles().values().forEach(bundle -> bundleKeys.add(bundle.path("key").asString("")));

            String baseName = StringUtils.hasText(body.fileName()) ? body.fileName().trim().replaceFirst("(?i)\\.zip$", "") : productId;
            List<JsonNode> saved = new ArrayList<>();
            for (String part : DownloadParts.ALL) {
                List<String> members = expand(artifacts, body.files(), part, null);
                String bundleKey = DownloadParts.key(productId, part);
                if (!members.isEmpty()) {
                    saved.add(downloads.saveBundle(bundleKey, bundleRequest(product.getName(), baseName, part, members)));
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

    // ---- helpers --------------------------------------------------------------------------------------

    /**
     * The current files of each chosen group's part, in the chosen order: the part's own old single file first, then
     * its uploaded files by name. Files with no finished upload are left out, so an upload in progress never breaks
     * a download. skip leaves out one file that is about to be deleted.
     */
    static List<String> expand(JsonNode artifacts, List<String> bases, String part, String skip) {
        List<String> members = new ArrayList<>();
        for (String base : bases) {
            List<String> entries = new ArrayList<>();
            String own = null;
            for (JsonNode artifact : artifacts.values()) {
                String key = artifact.path("key").asString("");
                DownloadParts.Parsed parsed = DownloadParts.parse(key);
                boolean uploaded = !artifact.path("currentVersionId").isNull() && !artifact.path("currentVersionId").isMissingNode();
                if (parsed == null || !parsed.base().equals(base) || !parsed.part().equals(part) || !uploaded || key.equals(skip)) {
                    continue;
                }
                if (parsed.entry() == null) {
                    own = key;
                } else {
                    entries.add(key);
                }
            }
            if (own != null) {
                members.add(own);
            }
            entries.stream().sorted().forEach(members::add);
        }
        return members;
    }

    /** Rebuilds the member lists of every product download that includes this group (optionally without one file). */
    private List<JsonNode> refreshProducts(String base, String skip) {
        JsonNode artifacts = downloads.artifacts();
        List<JsonNode> saved = new ArrayList<>();
        for (JsonNode bundle : downloads.bundles().values()) {
            String bundleKey = bundle.path("key").asString("");
            DownloadParts.Parsed parsed = DownloadParts.parse(bundleKey);
            if (parsed == null || parsed.entry() != null) {
                continue;
            }
            List<String> bases = new ArrayList<>();
            for (JsonNode member : bundle.path("artifacts").values()) {
                DownloadParts.Parsed memberParsed = DownloadParts.parse(member.asString(""));
                if (memberParsed != null && !bases.contains(memberParsed.base())) {
                    bases.add(memberParsed.base());
                }
            }
            if (!bases.contains(base)) {
                continue;
            }
            List<String> members = expand(artifacts, bases, parsed.part(), skip);
            List<String> current = new ArrayList<>();
            bundle.path("artifacts").values().forEach(member -> current.add(member.asString("")));
            if (members.equals(current)) {
                continue;
            }
            if (members.isEmpty()) {
                downloads.deleteBundle(bundleKey);
                continue;
            }
            String fileName = bundle.path("fileName").asString(bundleKey + ".zip");
            String displayName = bundle.path("displayName").asString(bundleKey);
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("displayName", displayName);
            request.put("fileName", fileName);
            request.put("artifacts", members);
            saved.add(downloads.saveBundle(bundleKey, request));
        }
        return saved;
    }

    /** Puts every file of the group in its place under a new mod folder. */
    private void placeFiles(String base, String folder) {
        for (JsonNode artifact : downloads.artifacts().values()) {
            String key = artifact.path("key").asString("");
            DownloadParts.Parsed parsed = DownloadParts.parse(key);
            if (parsed == null || !parsed.base().equals(base)) {
                continue;
            }
            // The file's own name is in its newest version (an old single file) or its display name (an entry)
            JsonNode versions = artifact.path("versions");
            String fileName = parsed.entry() == null
                ? (versions.isArray() && !versions.isEmpty() ? versions.get(0).path("fileName").asString("") : "")
                : artifact.path("displayName").asString("");
            if (parsed.entry() == null && fileName.isEmpty()) {
                continue;
            }
            downloads.saveArtifact(key, artifact.path("displayName").asString(key), DownloadParts.folderFor(folder, fileName));
        }
    }

    private static Map<String, Object> bundleRequest(String productName, String baseName, String part, List<String> members) {
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("displayName", productName + " (" + DownloadParts.label(part) + ")");
        bundle.put("fileName", baseName + "-" + DownloadParts.label(part) + ".zip");
        bundle.put("artifacts", members);
        return bundle;
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
