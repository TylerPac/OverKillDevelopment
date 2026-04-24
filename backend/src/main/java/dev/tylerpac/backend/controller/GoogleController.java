package dev.tylerpac.backend.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.tylerpac.backend.model.User;
import dev.tylerpac.backend.model.UserGoogleCredential;
import dev.tylerpac.backend.model.UserTokenPurpose;
import dev.tylerpac.backend.repo.UserGoogleCredentialRepository;
import dev.tylerpac.backend.repo.UserRepository;
import dev.tylerpac.backend.repo.UserTemplateRepository;
import dev.tylerpac.backend.security.JwtUtil;
import dev.tylerpac.backend.service.CryptoUtil;
import dev.tylerpac.backend.service.GoogleOAuthService;
import dev.tylerpac.backend.service.SheetsParsingService;
import dev.tylerpac.backend.service.UserTokenService;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/google")
public class GoogleController {

    private final UserRepository userRepository;
    private final UserTokenService userTokenService;
    private final GoogleOAuthService googleOAuthService;
    private final UserGoogleCredentialRepository userGoogleCredentialRepository;
    private final CryptoUtil cryptoUtil;
    private final SheetsParsingService sheetsParsingService;
    private final UserTemplateRepository userTemplateRepository;
    private final String frontendBaseUrl;
    private final dev.tylerpac.backend.security.JwtUtil jwtUtil;

    public GoogleController(
        UserRepository userRepository,
        UserTokenService userTokenService,
        GoogleOAuthService googleOAuthService,
        UserGoogleCredentialRepository userGoogleCredentialRepository,
        CryptoUtil cryptoUtil,
        SheetsParsingService sheetsParsingService,
        UserTemplateRepository userTemplateRepository,
        JwtUtil jwtUtil,
        @Value("${app.auth.frontend-base-url:http://localhost:5173}") String frontendBaseUrl
    ) {
        this.userRepository = userRepository;
        this.userTokenService = userTokenService;
        this.googleOAuthService = googleOAuthService;
        this.userGoogleCredentialRepository = userGoogleCredentialRepository;
        this.cryptoUtil = cryptoUtil;
        this.sheetsParsingService = sheetsParsingService;
        this.userTemplateRepository = userTemplateRepository;
        this.jwtUtil = jwtUtil;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @GetMapping("/template-info")
    public ResponseEntity<?> templateInfo() {
        String templateId = System.getenv("GOOGLE_TEMPLATE_SPREADSHEET_ID");
        if (templateId == null || templateId.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("no_template_configured");
        String copyUrl = "https://docs.google.com/spreadsheets/d/" + templateId + "/copy";
        return ResponseEntity.ok(Map.of("templateId", templateId, "copyUrl", copyUrl));
    }

    @PostMapping("/register-template")
    public ResponseEntity<?> registerTemplate(HttpServletRequest request, @RequestBody(required = false) Map<String, String> body) {
        User user = resolveCurrentUser(request);
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("unauthorized");

        if (body == null || !body.containsKey("spreadsheetId")) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("missing_spreadsheetId");
        String spreadsheetId = body.get("spreadsheetId");

        // If no name provided, fetch the actual Google Sheet title so the UI stays in sync
        String providedName = body.get("name");
        String name;
        if (providedName != null && !providedName.isBlank()) {
            name = providedName;
        } else {
            name = spreadsheetId; // fallback — overwritten below if metadata succeeds
        }

        Optional<UserGoogleCredential> credOpt = userGoogleCredentialRepository.findByUser(user);
        if (credOpt.isEmpty()) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("no_google_linked");

        String appSecret = System.getenv("APP_SECRET");
        if (appSecret == null) appSecret = "dev_insecure_secret";
        String refreshToken = cryptoUtil.decrypt(appSecret, credOpt.get().getEncryptedRefreshToken());
        String accessToken = googleOAuthService.accessTokenFromRefreshToken(refreshToken);

        try {
            // When no name provided, fetch the actual Google Sheet title for display sync
            if (providedName == null || providedName.isBlank()) {
                try {
                    JsonNode meta = googleOAuthService.fetchSpreadsheetMetadata(accessToken, spreadsheetId);
                    String title = meta.path("properties").path("title").asText(null);
                    if (title != null && !title.isBlank()) name = title;
                } catch (Exception ignored) {}
            }

            Object parsed = fetchAndParseAllTabs(accessToken, spreadsheetId);

            // persist template record for user
            var existing = userTemplateRepository.findByUserAndSpreadsheetId(user, spreadsheetId);
            var now = java.time.Instant.now();
            if (existing.isPresent()) {
                var ut = existing.get();
                ut.setName(name);
                ut.setUpdatedAt(now);
                userTemplateRepository.save(ut);
            } else {
                var ut = new dev.tylerpac.backend.model.UserTemplate();
                ut.setUser(user);
                ut.setSpreadsheetId(spreadsheetId);
                ut.setName(name);
                ut.setCreatedAt(now);
                ut.setUpdatedAt(now);
                userTemplateRepository.save(ut);
            }

            return ResponseEntity.ok(Map.of("spreadsheetId", spreadsheetId, "name", name, "parsed", parsed));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            String msg = ex.getMessage() == null ? "fetch_failed" : ex.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(msg);
        }
    }

    @GetMapping("/templates")
    public ResponseEntity<?> listUserTemplates(HttpServletRequest request) {
        User user = resolveCurrentUser(request);
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("unauthorized");

        // Resolve access token once for name-sync calls
        String accessToken = null;
        try {
            Optional<UserGoogleCredential> credOpt = userGoogleCredentialRepository.findByUser(user);
            if (credOpt.isPresent()) {
                String appSecret = System.getenv("APP_SECRET");
                if (appSecret == null) appSecret = "dev_insecure_secret";
                String refreshToken = cryptoUtil.decrypt(appSecret, credOpt.get().getEncryptedRefreshToken());
                accessToken = googleOAuthService.accessTokenFromRefreshToken(refreshToken);
            }
        } catch (Exception ignored) {}

        var list = userTemplateRepository.findByUser(user);

        // Sync names from Google in case the user renamed their sheet
        if (accessToken != null) {
            final String token = accessToken;
            var now = java.time.Instant.now();
            for (var t : list) {
                try {
                    JsonNode meta = googleOAuthService.fetchSpreadsheetMetadata(token, t.getSpreadsheetId());
                    String title = meta.path("properties").path("title").asText(null);
                    if (title != null && !title.isBlank() && !title.equals(t.getName())) {
                        t.setName(title);
                        t.setUpdatedAt(now);
                        userTemplateRepository.save(t);
                    }
                } catch (Exception ignored) {}
            }
        }

        var out = list.stream().map(t -> Map.of(
            "spreadsheetId", t.getSpreadsheetId(),
            "name", t.getName(),
            "createdAt", t.getCreatedAt(),
            "updatedAt", t.getUpdatedAt()
        )).toList();
        return ResponseEntity.ok(out);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/templates")
    public ResponseEntity<?> deleteTemplate(HttpServletRequest request, @RequestParam("spreadsheetId") String spreadsheetId) {
        User user = resolveCurrentUser(request);
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("unauthorized");
        var existing = userTemplateRepository.findByUserAndSpreadsheetId(user, spreadsheetId);
        if (existing.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("not_found");
        userTemplateRepository.delete(existing.get());
        return ResponseEntity.ok(Map.of("deleted", spreadsheetId));
    }

    @GetMapping("/login-url")
    public ResponseEntity<?> loginUrl(HttpServletRequest request) {
        User user = resolveCurrentUser(request);
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("unauthorized");

        String state = userTokenService.issueToken(user, UserTokenPurpose.GOOGLE_LINK, Duration.ofMinutes(10));
        String url = googleOAuthService.buildLoginUrl(state);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @GetMapping("/callback")
    public ResponseEntity<?> callback(
        @RequestParam(value = "code", required = false) String code,
        @RequestParam(value = "state", required = false) String state,
        @RequestParam(value = "error", required = false) String error
    ) {
        // If Google returned an error (e.g. user denied consent), forward it to the frontend
        if (error != null && !error.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", frontendBaseUrl + "/google-callback?error=" + urlEncode(error))
                .build();
        }

        if (state == null || state.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", frontendBaseUrl + "/google-callback?error=" + urlEncode("invalid_or_missing_state"))
                .build();
        }

        Optional<User> userOpt = userTokenService.consumeToken(state, UserTokenPurpose.GOOGLE_LINK);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", frontendBaseUrl + "/google-callback?error=" + urlEncode("invalid_or_expired_state"))
                .build();
        }

        if (code == null || code.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", frontendBaseUrl + "/google-callback?error=" + urlEncode("no_code"))
                .build();
        }

        try {
            GoogleOAuthService.Tokens tokens = googleOAuthService.exchangeCodeForTokens(code);
            User user = userOpt.get();

            if (tokens.refreshToken() == null || tokens.refreshToken().isEmpty()) {
                return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", frontendBaseUrl + "/google-callback?error=" + urlEncode("no_refresh_token"))
                    .build();
            }

            UserGoogleCredential cred = userGoogleCredentialRepository.findByUser(user).orElseGet(() -> {
                UserGoogleCredential g = new UserGoogleCredential();
                g.setUser(user);
                return g;
            });

            String appSecret = System.getenv("APP_SECRET");
            if (appSecret == null) appSecret = "dev_insecure_secret";
            String encrypted = cryptoUtil.encrypt(appSecret, tokens.refreshToken());
            cred.setEncryptedRefreshToken(encrypted);
            userGoogleCredentialRepository.save(cred);

            return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", frontendBaseUrl + "/google-callback?status=" + urlEncode("linked"))
                .build();
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", frontendBaseUrl + "/google-callback?error=" + urlEncode("internal_error"))
                .build();
        }
    }

    @PostMapping("/copy-template")
    public ResponseEntity<?> copyTemplate(HttpServletRequest request, @RequestBody(required = false) Map<String, String> body) {
        User user = resolveCurrentUser(request);
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("unauthorized");

        Optional<UserGoogleCredential> credOpt = userGoogleCredentialRepository.findByUser(user);
        if (credOpt.isEmpty()) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("no_google_linked");

        String appSecret = System.getenv("APP_SECRET");
        if (appSecret == null) appSecret = "dev_insecure_secret";
        String refreshToken = cryptoUtil.decrypt(appSecret, credOpt.get().getEncryptedRefreshToken());
        String accessToken = googleOAuthService.accessTokenFromRefreshToken(refreshToken);

        String templateId = System.getenv("GOOGLE_TEMPLATE_SPREADSHEET_ID");
        String name = body != null && body.containsKey("name") ? body.get("name") : "Loot Table - Copy";
        String tab = body != null && body.containsKey("tab") ? body.get("tab") : "Sheet1";
        try {
            String newId = null;
            JsonNode createdMeta = null;
            boolean usedDriveCopy = false;

            // First, try Drive's files.copy which preserves formatting/formatting and other metadata.
            try {
                JsonNode copyResp = googleOAuthService.copyFileAs(accessToken, templateId, name);
                newId = copyResp.path("id").asText("");
                // fetch metadata for sheets
                if (newId != null && !newId.isEmpty()) {
                    createdMeta = googleOAuthService.fetchSpreadsheetMetadata(accessToken, newId);
                    usedDriveCopy = true;
                }
            } catch (Exception driveEx) {
                // Drive copy failed, fall back to sheets.copyTo
            }

            // Second attempt: sheets.copyTo — copies each sheet WITH formatting using Sheets API
            if (newId == null || newId.isEmpty()) {
                try {
                    createdMeta = googleOAuthService.copySpreadsheetWithFormatting(accessToken, templateId, name);
                    newId = createdMeta.path("spreadsheetId").asText("");
                    if (newId != null && !newId.isEmpty()) usedDriveCopy = true;
                } catch (Exception copyEx) {
                    // sheets.copyTo failed, fall back to plain create+values
                }
            }

            if (newId == null || newId.isEmpty()) {
                createdMeta = googleOAuthService.createSpreadsheetFromTemplate(accessToken, templateId, name);
                newId = createdMeta.path("spreadsheetId").asText("");
                if (newId == null || newId.isEmpty()) return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("create_failed");
            }

            // Parse all 3 tabs exactly like the Python reference parser
            Object parsed = fetchAndParseAllTabs(accessToken, newId);

            var existing = userTemplateRepository.findByUserAndSpreadsheetId(user, newId);
            var now = java.time.Instant.now();
            if (existing.isPresent()) {
                var ut = existing.get();
                ut.setName(name);
                ut.setUpdatedAt(now);
                userTemplateRepository.save(ut);
            } else {
                var ut = new dev.tylerpac.backend.model.UserTemplate();
                ut.setUser(user);
                ut.setSpreadsheetId(newId);
                ut.setName(name);
                ut.setCreatedAt(now);
                ut.setUpdatedAt(now);
                userTemplateRepository.save(ut);
            }

            return ResponseEntity.ok(Map.of(
                "spreadsheetId", newId,
                "parsed", parsed,
                "name", name,
                "usedDriveCopy", usedDriveCopy
            ));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            String msg = ex.getMessage() == null ? "create_failed" : ex.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(msg);
        }
    }

    @GetMapping("/fetch")
    public ResponseEntity<?> fetchParsed(
        HttpServletRequest request,
        @RequestParam("spreadsheetId") String spreadsheetId,
        @RequestParam(value = "tab", required = false, defaultValue = "") String tab
    ) {
        User user = resolveCurrentUser(request);
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("unauthorized");

        Optional<UserGoogleCredential> credOpt = userGoogleCredentialRepository.findByUser(user);
        if (credOpt.isEmpty()) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("no_google_linked");

        String appSecret = System.getenv("APP_SECRET");
        if (appSecret == null) appSecret = "dev_insecure_secret";
        String refreshToken = cryptoUtil.decrypt(appSecret, credOpt.get().getEncryptedRefreshToken());
        String accessToken = googleOAuthService.accessTokenFromRefreshToken(refreshToken);

        // If caller requests a specific non-crate tab (e.g. tier/zone), fall back to single-tab parse
        if (tab != null && !tab.isBlank() && (tab.toLowerCase().contains("tier") || tab.toLowerCase().contains("zone"))) {
            try {
                JsonNode raw = googleOAuthService.fetchSheetValues(accessToken, spreadsheetId, tab + "!A:Z");
                return ResponseEntity.ok(sheetsParsingService.parseAsTierZones(raw));
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("sheets_fetch_failed");
            }
        }

        // Standard multi-tab crate settings parse (LootTables + ItemProfiles + AttachmentProfiles)
        try {
            Object parsed = fetchAndParseAllTabs(accessToken, spreadsheetId);
            return ResponseEntity.ok(parsed);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("sheets_fetch_failed");
        }
    }

    /**
     * Fetch all 3 standard crate-settings tabs (LootTables, ItemProfiles, AttachmentProfiles)
     * from the given spreadsheet and parse them — exactly matching the Python reference parser.
     */
    private Object fetchAndParseAllTabs(String accessToken, String spreadsheetId) {
        JsonNode lootRaw = fetchTabSafe(accessToken, spreadsheetId, SheetsParsingService.TAB_LOOT_TABLES);
        JsonNode itemRaw = fetchTabSafe(accessToken, spreadsheetId, SheetsParsingService.TAB_ITEM_PROFILES);
        JsonNode attachRaw = fetchTabSafe(accessToken, spreadsheetId, SheetsParsingService.TAB_ATTACHMENT_PROFILES);
        return sheetsParsingService.parseAsCrateSettingsFromTabs(lootRaw, itemRaw, attachRaw);
    }

    /** Fetch a tab's values; returns null on failure so parsers get an empty result gracefully. */
    private JsonNode fetchTabSafe(String accessToken, String spreadsheetId, String tabName) {
        try {
            return googleOAuthService.fetchSheetValues(accessToken, spreadsheetId, tabName + "!A:ZZ");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Fetch and parse a publicly-shared spreadsheet using the server API key.
     * This does not require the user to have linked their Google account.
     */
    @GetMapping("/fetch-public")
    public ResponseEntity<?> fetchPublic(
        @RequestParam("spreadsheetId") String spreadsheetId,
        @RequestParam(value = "tab", required = false, defaultValue = "Sheet1") String tab
    ) {
        String apiKey = System.getenv("GOOGLE_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("no_api_key");

        JsonNode raw = googleOAuthService.fetchSheetValuesWithApiKey(apiKey, spreadsheetId, tab + "!A:Z");
        String tabLower = tab == null ? "" : tab.toLowerCase();
        Object parsed;
        if (tabLower.contains("tier") || tabLower.contains("zone")) {
            parsed = sheetsParsingService.parseAsTierZones(raw);
        } else {
            parsed = sheetsParsingService.parseAsCrateSettings(raw);
        }

        return ResponseEntity.ok(parsed);
    }

    private User resolveCurrentUser(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        String token = header.substring(7);
        if (!jwtUtil.validateToken(token)) return null;
        String username = jwtUtil.extractUsername(token);
        return userRepository.findByUsername(username).orElse(null);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
