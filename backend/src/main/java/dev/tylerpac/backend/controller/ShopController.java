package dev.tylerpac.backend.controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;

import dev.tylerpac.backend.dto.CreateCartCheckoutSessionRequest;
import dev.tylerpac.backend.dto.CreateCheckoutSessionRequest;
import dev.tylerpac.backend.dto.CreateCheckoutSessionResponse;
import dev.tylerpac.backend.dto.ShopOrderResponse;
import dev.tylerpac.backend.dto.ShopProductResponse;
import dev.tylerpac.backend.dto.SubscriptionStatusResponse;
import dev.tylerpac.backend.model.User;
import dev.tylerpac.backend.repo.UserRepository;
import dev.tylerpac.backend.service.ShopDownloadService;
import dev.tylerpac.backend.service.StripeShopService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/shop")
public class ShopController {

    private final StripeShopService stripeShopService;
    private final ShopDownloadService shopDownloadService;
    private final UserRepository userRepository;

    public ShopController(
        StripeShopService stripeShopService,
        ShopDownloadService shopDownloadService,
        UserRepository userRepository
    ) {
        this.stripeShopService = stripeShopService;
        this.shopDownloadService = shopDownloadService;
        this.userRepository = userRepository;
    }

    @GetMapping("/products")
    public ResponseEntity<List<ShopProductResponse>> products() {
        return ResponseEntity.ok(stripeShopService.getProducts());
    }

    @PostMapping("/checkout-session")
    public ResponseEntity<?> createCheckoutSession(
        @Valid @RequestBody CreateCheckoutSessionRequest request,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        Principal principal
    ) {
        try {
            User user = requireUser(principal);
            if (!StringUtils.hasText(user.getSteam64Id())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("account_not_setup");
            }
            CreateCheckoutSessionResponse response = stripeShopService.createCheckoutSession(user, request.getProductId(), idempotencyKey);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (StripeException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ex.getMessage());
        }
    }

    @GetMapping("/orders")
    public ResponseEntity<?> orders(Principal principal) {
        try {
            User user = requireUser(principal);
            List<ShopOrderResponse> orders = stripeShopService.getOrders(user);
            return ResponseEntity.ok(orders);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
        }
    }

    @PostMapping("/cart-checkout-session")
    public ResponseEntity<?> createCartCheckoutSession(
        @Valid @RequestBody CreateCartCheckoutSessionRequest request,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        Principal principal
    ) {
        try {
            User user = requireUser(principal);
            if (!StringUtils.hasText(user.getSteam64Id())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("account_not_setup");
            }
            CreateCheckoutSessionResponse response = stripeShopService.createCartCheckoutSession(user, request.getProductIds(), idempotencyKey);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (StripeException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ex.getMessage());
        }
    }

    @PostMapping("/checkout/sync")
    public ResponseEntity<?> syncCheckoutStatus(
        @RequestBody String sessionId,
        Principal principal
    ) {
        try {
            User user = requireUser(principal);
            stripeShopService.syncCheckoutStatusFromSession(user, sessionId);
            return ResponseEntity.ok("synced");
        } catch (IllegalArgumentException ex) {
            return switch (ex.getMessage()) {
                case "session_id_required", "invalid_checkout_session" ->
                    ResponseEntity.badRequest().body(ex.getMessage());
                case "session_user_mismatch" ->
                    ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
                default -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
            };
        } catch (StripeException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ex.getMessage());
        }
    }

    @PostMapping("/subscription/checkout-session")
    public ResponseEntity<?> createSubscriptionCheckoutSession(
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        Principal principal
    ) {
        try {
            User user = requireUser(principal);
            if (!StringUtils.hasText(user.getSteam64Id())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("account_not_setup");
            }
            CreateCheckoutSessionResponse response = stripeShopService.createSubscriptionCheckoutSession(user, idempotencyKey);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ex.getMessage());
        } catch (StripeException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ex.getMessage());
        }
    }

    @PostMapping("/subscription/sync")
    public ResponseEntity<?> syncSubscriptionStatus(
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody String sessionId,
        Principal principal
    ) {
        try {
            User user = requireUser(principal);
            stripeShopService.syncSubscriptionStatusFromSession(user, sessionId);
            return ResponseEntity.ok("synced");
        } catch (IllegalArgumentException ex) {
            return switch (ex.getMessage()) {
                case "session_id_required", "invalid_subscription_session" ->
                    ResponseEntity.badRequest().body(ex.getMessage());
                case "session_user_mismatch" ->
                    ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
                default -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
            };
        } catch (StripeException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ex.getMessage());
        }
    }

    @PostMapping("/subscription/cancel")
    public ResponseEntity<?> cancelSubscription(Principal principal) {
        try {
            User user = requireUser(principal);
            stripeShopService.cancelSubscription(user);
            return ResponseEntity.ok("subscription_canceled");
        } catch (IllegalArgumentException ex) {
            if ("subscription_not_found".equals(ex.getMessage())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
            }
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (StripeException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ex.getMessage());
        }
    }

    @GetMapping("/subscription-status")
    public ResponseEntity<?> subscriptionStatus(Principal principal) {
        try {
            User user = requireUser(principal);
            return ResponseEntity.ok(new SubscriptionStatusResponse(
                user.isPremiumUser(),
                user.getStripeSubscriptionStatus(),
                user.getStripeSubscriptionId(),
                user.isStripeSubscriptionCancelAtPeriodEnd(),
                user.getStripeSubscriptionCancelAt(),
                user.getStripeSubscriptionCurrentPeriodEnd(),
                true,
                StringUtils.hasText(user.getSteam64Id())
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
        }
    }

    @GetMapping("/download-link/{productId}")
    public ResponseEntity<?> getDownloadLink(@PathVariable String productId, Principal principal) {
        try {
            User user = requireUser(principal);
            Optional<String> link = shopDownloadService.findDownloadLink(user, productId);
            if (link.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(Map.of("url", link.get()));
        } catch (IllegalArgumentException ex) {
            return switch (ex.getMessage()) {
                case "account_not_setup", "purchase_required" ->
                    ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
                case "invalid_product" ->
                    ResponseEntity.badRequest().body(ex.getMessage());
                default -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
            };
        }
    }

    @GetMapping("/download/{productId}")
    public ResponseEntity<?> downloadProduct(@PathVariable String productId, Principal principal) {
        try {
            User user = requireUser(principal);
            ShopDownloadService.DownloadAsset asset = shopDownloadService.loadPaidProductAsset(user, productId);

            String contentType = StringUtils.hasText(asset.contentType())
                ? asset.contentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;

            Resource resource = asset.resource();
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + asset.fileName() + "\"")
                .body(resource);
        } catch (IllegalArgumentException ex) {
            return switch (ex.getMessage()) {
                case "account_not_setup", "purchase_required" ->
                    ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
                case "download_not_found" ->
                    ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
                case "invalid_product" ->
                    ResponseEntity.badRequest().body(ex.getMessage());
                default -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
            };
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("download_unavailable");
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<?> webhook(
        @RequestBody String payload,
        @RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature
    ) {
        if (!StringUtils.hasText(stripeSignature)) {
            return ResponseEntity.badRequest().body("missing_stripe_signature");
        }

        try {
            stripeShopService.handleWebhook(payload, stripeSignature);
            return ResponseEntity.ok("received");
        } catch (SignatureVerificationException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("invalid_signature");
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ex.getMessage());
        }
    }

    private User requireUser(Principal principal) {
        if (principal == null || !StringUtils.hasText(principal.getName())) {
            throw new IllegalArgumentException("unauthorized");
        }
        return userRepository.findByUsername(principal.getName())
            .orElseThrow(() -> new IllegalArgumentException("unauthorized"));
    }
}
