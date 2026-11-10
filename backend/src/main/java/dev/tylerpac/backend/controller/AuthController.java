package dev.tylerpac.backend.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.tylerpac.backend.model.User;
import dev.tylerpac.backend.model.UserGoogleCredential;
import dev.tylerpac.backend.model.UserTokenPurpose;
import dev.tylerpac.backend.repo.ShopOrderRepository;
import dev.tylerpac.backend.repo.UserGoogleCredentialRepository;
import dev.tylerpac.backend.repo.UserRepository;
import dev.tylerpac.backend.security.AdminAccess;
import dev.tylerpac.backend.service.CryptoUtil;
import dev.tylerpac.backend.service.DiscordOAuthService;
import dev.tylerpac.backend.service.GitHubOAuthService;
import dev.tylerpac.backend.service.GoogleOAuthService;
import dev.tylerpac.backend.service.SheetsParsingService;
import dev.tylerpac.backend.service.UserTokenService;
import dev.tylerpac.backend.util.JsonUtils;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserRepository userRepository;
    private final ShopOrderRepository shopOrderRepository;
    private final UserTokenService userTokenService;
    private final DiscordOAuthService discordOAuthService;
    private final GitHubOAuthService gitHubOAuthService;
    private final UserGoogleCredentialRepository userGoogleCredentialRepository;
    private final CryptoUtil cryptoUtil;
    private final GoogleOAuthService googleOAuthService;
    private final SheetsParsingService sheetsParsingService;
    private final Environment environment;
    private final String frontendBaseUrl;
    private final AdminAccess adminAccess;

    public AuthController(
        UserRepository userRepository,
        ShopOrderRepository shopOrderRepository,
        UserTokenService userTokenService,
        DiscordOAuthService discordOAuthService,
        GitHubOAuthService gitHubOAuthService,
        UserGoogleCredentialRepository userGoogleCredentialRepository,
        CryptoUtil cryptoUtil,
        GoogleOAuthService googleOAuthService,
        SheetsParsingService sheetsParsingService,
        Environment environment,
        @Value("${app.auth.frontend-base-url:http://localhost:5173}") String frontendBaseUrl,
        AdminAccess adminAccess
    ) {
        this.userRepository = userRepository;
        this.shopOrderRepository = shopOrderRepository;
        this.userTokenService = userTokenService;
        this.discordOAuthService = discordOAuthService;
        this.gitHubOAuthService = gitHubOAuthService;
        this.userGoogleCredentialRepository = userGoogleCredentialRepository;
        this.cryptoUtil = cryptoUtil;
        this.googleOAuthService = googleOAuthService;
        this.sheetsParsingService = sheetsParsingService;
        this.environment = environment;
        this.frontendBaseUrl = frontendBaseUrl;
        this.adminAccess = adminAccess;
    }

    @GetMapping("/discord/link-url")
    public ResponseEntity<?> discordLinkUrl(HttpServletRequest request) {
        User user = resolveCurrentUser(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("unauthorized");
        }

        String state = userTokenService.issueToken(
            user,
            UserTokenPurpose.DISCORD_LINK,
            Duration.ofMinutes(10)
        );

        String url = discordOAuthService.buildLinkUrl(state);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @GetMapping("/discord/callback")
    public ResponseEntity<?> discordCallback(
        @RequestParam("code") String code,
        @RequestParam("state") String state
    ) {
        Optional<User> userOpt = userTokenService.consumeToken(state, UserTokenPurpose.DISCORD_LINK);
        if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", frontendBaseUrl + "/discord-callback?error=" + urlEncode("invalid_or_expired_state"))
                    .build();
        }

        try {
            DiscordOAuthService.DiscordProfile profile = discordOAuthService.fetchProfileFromAuthorizationCode(code);
            User user = userRepository.findById(userOpt.get().getId()).orElseThrow();

            Optional<User> existingDiscordOwner = userRepository.findByDiscordUserId(profile.id());
            if (existingDiscordOwner.isPresent() && !existingDiscordOwner.get().getId().equals(user.getId())) {
                    return ResponseEntity.status(HttpStatus.FOUND)
                        .header("Location", frontendBaseUrl + "/discord-callback?error=" + urlEncode("discord_account_already_linked"))
                        .build();
            }

            user.setDiscordUserId(profile.id());
            user.setDiscordUsername(profile.username());
            userRepository.save(user);

                String redirect = frontendBaseUrl
                + "/discord-callback?status=" + urlEncode("discord_linked")
                + "&discordUserId=" + urlEncode(profile.id())
                + "&discordUsername=" + urlEncode(profile.username());
                return ResponseEntity.status(HttpStatus.FOUND).header("Location", redirect).build();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", frontendBaseUrl + "/discord-callback?error=" + urlEncode(ex.getMessage()))
                .build();
            } catch (DataIntegrityViolationException ex) {
                return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", frontendBaseUrl + "/discord-callback?error=" + urlEncode("discord_account_already_linked"))
                    .build();
            } catch (Exception ex) {
                log.error("Unexpected error during Discord callback", ex);
                return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", frontendBaseUrl + "/discord-callback?error=" + urlEncode("internal_error"))
                    .build();
        }
    }

    @GetMapping("/github/link-url")
    public ResponseEntity<?> githubLinkUrl(HttpServletRequest request) {
        User user = resolveCurrentUser(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("unauthorized");
        }

        String state = userTokenService.issueToken(
            user,
            UserTokenPurpose.GITHUB_LINK,
            Duration.ofMinutes(10)
        );

        String url = gitHubOAuthService.buildLinkUrl(state);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @GetMapping("/github/callback")
    public ResponseEntity<?> githubCallback(
        @RequestParam("code") String code,
        @RequestParam("state") String state
    ) {
        Optional<User> userOpt = userTokenService.consumeToken(state, UserTokenPurpose.GITHUB_LINK);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", frontendBaseUrl + "/github-callback?error=" + urlEncode("invalid_or_expired_state"))
                .build();
        }

        try {
            GitHubOAuthService.GitHubProfile profile = gitHubOAuthService.fetchProfileFromAuthorizationCode(code);
            User user = userRepository.findById(userOpt.get().getId()).orElseThrow();

            Optional<User> existingOwner = userRepository.findByGithubUserId(profile.id());
            if (existingOwner.isPresent() && !existingOwner.get().getId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", frontendBaseUrl + "/github-callback?error=" + urlEncode("github_account_already_linked"))
                    .build();
            }

            user.setGithubUserId(profile.id());
            user.setGithubUsername(profile.login());
            userRepository.save(user);

            String redirect = frontendBaseUrl
                + "/github-callback?status=" + urlEncode("github_linked")
                + "&githubUsername=" + urlEncode(profile.login());
            return ResponseEntity.status(HttpStatus.FOUND).header("Location", redirect).build();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", frontendBaseUrl + "/github-callback?error=" + urlEncode(ex.getMessage()))
                .build();
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", frontendBaseUrl + "/github-callback?error=" + urlEncode("github_account_already_linked"))
                .build();
        } catch (Exception ex) {
            log.error("Unexpected error during GitHub callback", ex);
            return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", frontendBaseUrl + "/github-callback?error=" + urlEncode("internal_error"))
                .build();
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request, CsrfToken csrfToken) {
        // Touching the token makes Spring Security write the XSRF-TOKEN cookie the frontend sends back.
        csrfToken.getToken();
        User user = resolveCurrentUser(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("unauthorized");
        }

        // Get all paid orders for this user
        java.util.List<dev.tylerpac.backend.model.ShopOrder> orders = shopOrderRepository.findByUserOrderByCreatedAtDesc(user);
        java.util.Set<String> ownedProducts = new java.util.HashSet<>();
        for (dev.tylerpac.backend.model.ShopOrder order : orders) {
            if ("PAID".equalsIgnoreCase(order.getStatus())) {
                ownedProducts.add(order.getProductId());
            }
        }

        // For each owned product, get the repo URLs
        java.util.Map<String, java.util.List<String>> githubReposByProduct = new java.util.HashMap<>();
        for (String productId : ownedProducts) {
            String prop = "app.shop.github-repo." + productId;
            String repoList = environment.getProperty(prop, "");
            if (!repoList.isBlank()) {
                java.util.List<String> links = new java.util.ArrayList<>();
                for (String repo : repoList.split(",")) {
                    repo = repo.trim();
                    if (!repo.isEmpty() && repo.contains("/")) {
                        links.add("https://github.com/" + repo);
                    }
                }
                if (!links.isEmpty()) githubReposByProduct.put(productId, links);
            }
        }

        return ResponseEntity.ok(Map.ofEntries(
            Map.entry("id", user.getId()),
            Map.entry("username", user.getUsername()),
            Map.entry("steam64Id", user.getSteam64Id()),
            Map.entry("discordUserId", user.getDiscordUserId() == null ? "" : user.getDiscordUserId()),
            Map.entry("discordUsername", user.getDiscordUsername() == null ? "" : user.getDiscordUsername()),
            Map.entry("githubUserId", user.getGithubUserId() == null ? "" : user.getGithubUserId()),
            Map.entry("githubUsername", user.getGithubUsername() == null ? "" : user.getGithubUsername()),
            Map.entry("emailVerified", user.isEmailVerified()),
            Map.entry("accountSetupComplete", StringUtils.hasText(user.getSteam64Id())),
            Map.entry("premiumUser", user.isPremiumUser()),
            Map.entry("subscriptionStatus", user.getStripeSubscriptionStatus() == null ? "none" : user.getStripeSubscriptionStatus()),
            Map.entry("githubReposByProduct", githubReposByProduct),
            // Checked with the login service each time, so the admin menu follows changes straight away
            Map.entry("admin", adminAccess.isAdmin(user.getSteam64Id()))
        ));
    }

    private User resolveCurrentUser(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();
        return principal == null ? null : userRepository.findByUsername(principal.getName()).orElse(null);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @PostMapping("/dev-copy")
    public ResponseEntity<?> devCopy(@RequestBody(required = false) Map<String, String> body) {
        // DEV ONLY: copy the canonical template into a user's Drive using stored credentials
        String profile = System.getenv("SPRING_PROFILES_ACTIVE");
        if (profile == null || !profile.contains("dev")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("not_allowed");
        }

        if (body == null || !body.containsKey("userId")) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("missing_userId");
        long userId;
        try { userId = Long.parseLong(body.get("userId")); } catch (NumberFormatException e) { return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("invalid_userId"); }

        var userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("user_not_found");
        var user = userOpt.get();

        Optional<UserGoogleCredential> credOpt = userGoogleCredentialRepository.findByUser(user);
        if (credOpt.isEmpty()) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("no_google_linked");

        String appSecret = System.getenv("APP_SECRET");
        if (appSecret == null) appSecret = "dev_insecure_secret";
        String refreshToken = cryptoUtil.decrypt(appSecret, credOpt.get().getEncryptedRefreshToken());
        String accessToken = googleOAuthService.accessTokenFromRefreshToken(refreshToken);

        String templateId = System.getenv("GOOGLE_TEMPLATE_SPREADSHEET_ID");
        String name = body.containsKey("name") ? body.get("name") : "Dev Copy";
        String tab = body.containsKey("tab") ? body.get("tab") : "Sheet1";

        try {
            String newId = "";
            JsonNode createdMeta;
            boolean usedDriveCopy = false;

            try {
                JsonNode copyResp = googleOAuthService.copyFileAs(accessToken, templateId, name);
                String tmpId = JsonUtils.textOrNull(copyResp.path("id"));
                newId = tmpId == null ? "" : tmpId;
                if (!newId.isEmpty()) {
                    usedDriveCopy = true;
                }
            } catch (Exception ignored) {}

            if (newId.isEmpty()) {
                createdMeta = googleOAuthService.createSpreadsheetFromTemplate(accessToken, templateId, name);
                String tmpNewId = JsonUtils.textOrNull(createdMeta.path("spreadsheetId"));
                newId = tmpNewId == null ? "" : tmpNewId;
                if (newId.isEmpty()) return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("create_failed");
            }

            // try to fetch values
            JsonNode raw = null;
            try { raw = googleOAuthService.fetchSheetValues(accessToken, newId, tab + "!A:ZZ"); } catch (Exception e) {
                try {
                    JsonNode meta = googleOAuthService.fetchSpreadsheetMetadata(accessToken, newId);
                    JsonNode sheets = meta.path("sheets");
                    if (sheets.isArray() && sheets.size() > 0) {
                        String first = JsonUtils.textOrNull(sheets.get(0).path("properties").path("title"));
                        if (first != null && !first.isBlank()) raw = googleOAuthService.fetchSheetValues(accessToken, newId, first + "!A:ZZ");
                    }
                } catch (Exception ex) { }
            }

            if (raw == null) return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("sheets_fetch_failed");

            String tabLower = tab == null ? "" : tab.toLowerCase();
            Object parsed = tabLower.contains("tier") || tabLower.contains("zone") ? sheetsParsingService.parseAsTierZones(raw) : sheetsParsingService.parseAsCrateSettings(raw);

            return ResponseEntity.ok(Map.of("spreadsheetId", newId, "parsed", parsed, "name", name, "tab", tab, "usedDriveCopy", usedDriveCopy));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }
}
