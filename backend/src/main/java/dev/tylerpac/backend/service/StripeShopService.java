package dev.tylerpac.backend.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;

import dev.tylerpac.backend.dto.AccessCodeAdminResponse;
import dev.tylerpac.backend.dto.CreateCheckoutSessionResponse;
import dev.tylerpac.backend.dto.CreateFullAccessCodeResponse;
import dev.tylerpac.backend.dto.RedeemFullAccessCodeResponse;
import dev.tylerpac.backend.dto.ShopOrderResponse;
import dev.tylerpac.backend.dto.ShopProductResponse;
import dev.tylerpac.backend.model.ProcessedStripeEvent;
import dev.tylerpac.backend.model.ShopAccessCode;
import dev.tylerpac.backend.model.ShopOrder;
import dev.tylerpac.backend.model.User;
import dev.tylerpac.backend.repo.ProcessedStripeEventRepository;
import dev.tylerpac.backend.repo.ShopAccessCodeRepository;
import dev.tylerpac.backend.repo.ShopOrderRepository;
import dev.tylerpac.backend.repo.UserRepository;

@Service
public class StripeShopService {

    private static final Logger log = LoggerFactory.getLogger(StripeShopService.class);

    private static final String DEFAULT_FRONTEND_URL = "http://localhost:5173";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PAID = "PAID";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_EXPIRED = "EXPIRED";
    private static final String ACCESS_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom ACCESS_CODE_RANDOM = new SecureRandom();

    private final ShopAccessCodeRepository shopAccessCodeRepository;
    private final ShopOrderRepository shopOrderRepository;
    private final ProcessedStripeEventRepository processedStripeEventRepository;
    private final PurchaseEmailService purchaseEmailService;
    private final UserRepository userRepository;
    private final GitHubRepoService gitHubRepoService;
    private final String currency;
    private final String successUrl;
    private final String cancelUrl;
    private final String webhookSecret;
    private final String premiumPriceId;
    private final String shopAdminToken;

    public StripeShopService(
        ShopAccessCodeRepository shopAccessCodeRepository,
        ShopOrderRepository shopOrderRepository,
        ProcessedStripeEventRepository processedStripeEventRepository,
        PurchaseEmailService purchaseEmailService,
        UserRepository userRepository,
        GitHubRepoService gitHubRepoService,
        @Value("${app.shop.currency:usd}") String currency,
        @Value("${app.shop.success-url}") String successUrl,
        @Value("${app.shop.cancel-url}") String cancelUrl,
        @Value("${app.shop.admin-token:}") String shopAdminToken,
        @Value("${app.stripe.secret-key:}") String stripeSecretKey,
        @Value("${app.stripe.webhook-secret:}") String webhookSecret,
        @Value("${app.stripe.premium-price-id:}") String premiumPriceId
    ) {
        this.shopAccessCodeRepository = shopAccessCodeRepository;
        this.shopOrderRepository = shopOrderRepository;
        this.processedStripeEventRepository = processedStripeEventRepository;
        this.purchaseEmailService = purchaseEmailService;
        this.userRepository = userRepository;
        this.gitHubRepoService = gitHubRepoService;
        this.currency = currency;
        this.successUrl = normalizeCheckoutBaseUrl(successUrl);
        this.cancelUrl = normalizeCheckoutBaseUrl(cancelUrl);
        this.shopAdminToken = shopAdminToken;
        this.webhookSecret = webhookSecret;
        this.premiumPriceId = premiumPriceId;

        if (!StringUtils.hasText(stripeSecretKey)) {
            throw new IllegalStateException("Stripe secret key is missing. Set APP_STRIPE_SECRET_KEY.");
        }
        Stripe.apiKey = stripeSecretKey;
    }

    private String normalizeCheckoutBaseUrl(String configuredUrl) {
        if (!StringUtils.hasText(configuredUrl)) {
            return DEFAULT_FRONTEND_URL;
        }

        String candidate = configuredUrl.trim();
        if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
            return candidate;
        }

        return DEFAULT_FRONTEND_URL;
    }

    public List<ShopProductResponse> getProducts() {
        return List.copyOf(catalog().values());
    }

    @Transactional
    public CreateFullAccessCodeResponse createAccessCode(String requestedCode, List<String> requestedProductIds, String adminToken) {
        requireAdminToken(adminToken);

        String normalizedCode = normalizeAccessCode(requestedCode, true);
        if (!StringUtils.hasText(normalizedCode)) {
            normalizedCode = generateUniqueAccessCode();
        }

        if (shopAccessCodeRepository.existsByCode(normalizedCode)) {
            throw new IllegalArgumentException("code_already_exists");
        }

        List<ShopProductResponse> scopedProducts = resolveScopedProducts(requestedProductIds);
        boolean fullAccess = scopedProducts.isEmpty();

        ShopAccessCode accessCode = new ShopAccessCode();
        accessCode.setCode(normalizedCode);
        accessCode.setFullAccess(fullAccess);
        accessCode.setProductIdsCsv(fullAccess
            ? null
            : scopedProducts.stream().map(ShopProductResponse::getId).collect(Collectors.joining(",")));

        try {
            shopAccessCodeRepository.save(accessCode);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("code_already_exists");
        }

        List<String> scopedProductIds = fullAccess
            ? List.of()
            : scopedProducts.stream().map(ShopProductResponse::getId).toList();

        return new CreateFullAccessCodeResponse(
            normalizedCode,
            true,
            fullAccess,
            scopedProductIds,
            fullAccess ? getProducts().size() : scopedProductIds.size()
        );
    }

    @Transactional
    public CreateFullAccessCodeResponse createFullAccessCode(String requestedCode, String adminToken) {
        return createAccessCode(requestedCode, Collections.emptyList(), adminToken);
    }

    /** Creates an access code without the admin-token check — for use by JWT-authenticated admin endpoints only. */
    @Transactional
    public CreateFullAccessCodeResponse createAccessCodeTrusted(String requestedCode, List<String> requestedProductIds) {
        String normalizedCode = normalizeAccessCode(requestedCode, true);
        if (!StringUtils.hasText(normalizedCode)) {
            normalizedCode = generateUniqueAccessCode();
        }

        if (shopAccessCodeRepository.existsByCode(normalizedCode)) {
            throw new IllegalArgumentException("code_already_exists");
        }

        List<ShopProductResponse> scopedProducts = resolveScopedProducts(requestedProductIds);
        boolean fullAccess = scopedProducts.isEmpty();

        ShopAccessCode accessCode = new ShopAccessCode();
        accessCode.setCode(normalizedCode);
        accessCode.setFullAccess(fullAccess);
        accessCode.setProductIdsCsv(fullAccess
            ? null
            : scopedProducts.stream().map(ShopProductResponse::getId).collect(Collectors.joining(",")));

        try {
            shopAccessCodeRepository.save(accessCode);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("code_already_exists");
        }

        List<String> scopedProductIds = fullAccess
            ? List.of()
            : scopedProducts.stream().map(ShopProductResponse::getId).toList();

        return new CreateFullAccessCodeResponse(
            normalizedCode,
            true,
            fullAccess,
            scopedProductIds,
            fullAccess ? getProducts().size() : scopedProductIds.size()
        );
    }

    public List<AccessCodeAdminResponse> listAllAccessCodes() {
        return shopAccessCodeRepository.findAllByOrderByCreatedAtDesc().stream()
            .map(ac -> {
                List<String> productIds = (ac.getProductIdsCsv() != null && !ac.getProductIdsCsv().isBlank())
                    ? List.of(ac.getProductIdsCsv().split(","))
                    : List.of();
                String redeemedByUsername = ac.getRedeemedByUser() != null
                    ? ac.getRedeemedByUser().getUsername() : null;
                String redeemedAt = ac.getRedeemedAt() != null
                    ? ac.getRedeemedAt().toString() : null;
                return new AccessCodeAdminResponse(
                    ac.getId(),
                    ac.getCode(),
                    ac.isFullAccess(),
                    productIds,
                    ac.getRedeemedAt() != null,
                    redeemedByUsername,
                    redeemedAt,
                    ac.isRevoked(),
                    ac.getCreatedAt().toString()
                );
            })
            .toList();
    }

    @Transactional
    public void revokeAccessCode(Long id) {
        ShopAccessCode accessCode = shopAccessCodeRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("access_code_not_found"));
        accessCode.setRevoked(true);
        shopAccessCodeRepository.save(accessCode);
    }

    @Transactional
    public void deleteAccessCode(Long id) {
        ShopAccessCode accessCode = shopAccessCodeRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("access_code_not_found"));
        if (!accessCode.isRevoked()) {
            throw new IllegalArgumentException("access_code_not_revoked");
        }
        shopAccessCodeRepository.delete(accessCode);
    }

    @Transactional
    public RedeemFullAccessCodeResponse redeemFullAccessCode(User user, String rawCode) {
        String code = normalizeAccessCode(rawCode, false);
        ShopAccessCode accessCode = shopAccessCodeRepository.findByCode(code)
            .orElseThrow(() -> new IllegalArgumentException("invalid_access_code"));

        if (accessCode.isRevoked()) {
            throw new IllegalArgumentException("invalid_access_code");
        }

        if (accessCode.getRedeemedAt() != null) {
            throw new IllegalArgumentException("access_code_already_redeemed");
        }

        List<ShopProductResponse> scopedProducts = accessCode.isFullAccess()
            ? getProducts()
            : resolveScopedProductsFromCode(accessCode);

        List<ShopProductResponse> productsToGrant = new ArrayList<>();
        for (ShopProductResponse product : scopedProducts) {
            boolean alreadyOwned = shopOrderRepository.existsByUserAndProductIdAndStatusIgnoreCase(user, product.getId(), STATUS_PAID);
            if (!alreadyOwned) {
                productsToGrant.add(product);
            }
        }

        if (productsToGrant.isEmpty()) {
            throw new IllegalArgumentException("nothing_to_redeem");
        }

        List<String> grantedProductIds = new ArrayList<>();
        for (ShopProductResponse product : productsToGrant) {
            ShopOrder order = new ShopOrder();
            order.setUser(user);
            order.setProductId(product.getId());
            order.setProductName(product.getName());
            order.setAmountCents(0L);
            order.setCurrency(product.getCurrency());
            order.setStatus(STATUS_PAID);
            order.setStripeCheckoutSessionId("promo:" + accessCode.getId() + ":" + product.getId());
            order.setStripePaymentIntentId("promo:" + accessCode.getId());
            shopOrderRepository.save(order);

            purchaseEmailService.sendOrderPaid(user, order);
            gitHubRepoService.grantRepoAccess(user.getGithubUsername(), product.getId());
            grantedProductIds.add(product.getId());
        }

        accessCode.setRedeemedByUser(user);
        accessCode.setRedeemedAt(Instant.now());
        shopAccessCodeRepository.save(accessCode);

        return new RedeemFullAccessCodeResponse(grantedProductIds.size(), grantedProductIds);
    }

    @Transactional
    public CreateCheckoutSessionResponse createSubscriptionCheckoutSession(User user, String idempotencyKey) throws StripeException {
        if (!StringUtils.hasText(premiumPriceId)) {
            throw new IllegalStateException("Stripe premium subscription price id is missing. Set APP_STRIPE_PREMIUM_PRICE_ID.");
        }
        if (!premiumPriceId.startsWith("price_")) {
            throw new IllegalStateException("APP_STRIPE_PREMIUM_PRICE_ID must be a Stripe Price ID (price_...), not a Product ID (prod_...).");
        }

        String scopedIdempotencyKey = normalizeSubscriptionIdempotencyKey(user, idempotencyKey);
        String customerId = ensureStripeCustomer(user);

        SessionCreateParams params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .setCustomer(customerId)
            .setSuccessUrl(successUrl + "?subscription=success&session_id={CHECKOUT_SESSION_ID}")
            .setCancelUrl(cancelUrl + "?subscription=cancel")
            .setClientReferenceId(String.valueOf(user.getId()))
            .putMetadata("userId", String.valueOf(user.getId()))
            .putMetadata("subscriptionType", "premium")
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setQuantity(1L)
                    .setPrice(premiumPriceId)
                    .build()
            )
            .build();

        RequestOptions requestOptions = RequestOptions.builder()
            .setIdempotencyKey(StringUtils.hasText(scopedIdempotencyKey) ? scopedIdempotencyKey : UUID.randomUUID().toString())
            .build();

        Session session = Session.create(params, requestOptions);
        return new CreateCheckoutSessionResponse(session.getUrl(), session.getId());
    }

    @Transactional
    public void syncSubscriptionStatusFromSession(User user, String sessionId) throws StripeException {
        if (!StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("session_id_required");
        }

        Session session = Session.retrieve(sessionId.trim());
        if (!"subscription".equalsIgnoreCase(session.getMode())) {
            throw new IllegalArgumentException("invalid_subscription_session");
        }

        String customerId = String.valueOf(session.getCustomer());
        if (!StringUtils.hasText(customerId) || "null".equals(customerId)) {
            throw new IllegalArgumentException("invalid_subscription_session");
        }

        if (!StringUtils.hasText(user.getStripeCustomerId())) {
            user.setStripeCustomerId(customerId);
            userRepository.save(user);
        } else if (!customerId.equals(user.getStripeCustomerId())) {
            throw new IllegalArgumentException("session_user_mismatch");
        }

        String subscriptionId = String.valueOf(session.getSubscription());
        if (!StringUtils.hasText(subscriptionId) || "null".equals(subscriptionId)) {
            return;
        }

        Subscription subscription = Subscription.retrieve(subscriptionId);
        updateUserSubscriptionFromSubscriptionEvent(subscription);
    }

    @Transactional
    public void cancelSubscription(User user) throws StripeException {
        if (!StringUtils.hasText(user.getStripeSubscriptionId())) {
            throw new IllegalArgumentException("subscription_not_found");
        }

        Subscription subscription = Subscription.retrieve(user.getStripeSubscriptionId());
        Subscription updatedSubscription = subscription.update(
            SubscriptionUpdateParams.builder()
                .setCancelAtPeriodEnd(true)
                .build()
        );
        updateUserSubscriptionFromSubscriptionEvent(updatedSubscription);
    }

    @Transactional
    public CreateCheckoutSessionResponse createCheckoutSession(User user, String productId, String idempotencyKey) throws StripeException {
        ShopProductResponse product = catalog().get(productId);
        if (product == null) {
            throw new IllegalArgumentException("invalid_product");
        }

        String scopedIdempotencyKey = normalizeIdempotencyKey(user, idempotencyKey);
        if (StringUtils.hasText(scopedIdempotencyKey)) {
            Optional<ShopOrder> existingOrder = shopOrderRepository.findByUserAndIdempotencyKey(user, scopedIdempotencyKey);
            if (existingOrder.isPresent()) {
                ShopOrder order = existingOrder.get();
                Session existingSession = Session.retrieve(order.getStripeCheckoutSessionId());
                return new CreateCheckoutSessionResponse(existingSession.getUrl(), existingSession.getId());
            }
        }

        String customerId = ensureStripeCustomer(user);

        SessionCreateParams.LineItem.PriceData.ProductData productData =
            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                .setName(product.getName())
                .setDescription(product.getDescription())
                .build();

        SessionCreateParams.LineItem.PriceData priceData = SessionCreateParams.LineItem.PriceData.builder()
            .setCurrency(product.getCurrency())
            .setUnitAmount(product.getAmountCents())
            .setProductData(productData)
            .build();

        SessionCreateParams params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.PAYMENT)
            .setCustomer(customerId)
            .setSuccessUrl(successUrl + "?checkout=success&session_id={CHECKOUT_SESSION_ID}")
            .setCancelUrl(cancelUrl + "?checkout=cancel")
            .setClientReferenceId(String.valueOf(user.getId()))
            .putMetadata("userId", String.valueOf(user.getId()))
            .putMetadata("productId", product.getId())
            .putMetadata("productName", product.getName())
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setQuantity(1L)
                    .setPriceData(priceData)
                    .build()
            )
            .build();

        RequestOptions requestOptions = RequestOptions.builder()
            .setIdempotencyKey(StringUtils.hasText(scopedIdempotencyKey) ? scopedIdempotencyKey : UUID.randomUUID().toString())
            .build();

        Session session = Session.create(params, requestOptions);

        ShopOrder order = new ShopOrder();
        order.setUser(user);
        order.setProductId(product.getId());
        order.setProductName(product.getName());
        order.setAmountCents(product.getAmountCents());
        order.setCurrency(product.getCurrency());
        order.setStatus(STATUS_PENDING);
        order.setStripeCheckoutSessionId(session.getId());
        order.setStripePaymentIntentId(session.getPaymentIntent());
        order.setIdempotencyKey(scopedIdempotencyKey);
        shopOrderRepository.save(order);
        purchaseEmailService.sendOrderPending(user, order);

        return new CreateCheckoutSessionResponse(session.getUrl(), session.getId());
    }

    @Transactional
    public CreateCheckoutSessionResponse createCartCheckoutSession(User user, List<String> productIds, String idempotencyKey) throws StripeException {
        if (productIds == null || productIds.isEmpty()) {
            throw new IllegalArgumentException("product_ids_required");
        }

        List<ShopProductResponse> products = productIds.stream()
            .distinct()
            .map(id -> {
                ShopProductResponse p = catalog().get(id);
                if (p == null) throw new IllegalArgumentException("invalid_product: " + id);
                return p;
            })
            .toList();

        String customerId = ensureStripeCustomer(user);

        // Store product IDs in metadata so the webhook can create orders on completion.
        // No order rows are created here — the unique constraint on stripeCheckoutSessionId
        // prevents multiple rows sharing one session ID.
        String productIdsCsv = products.stream()
            .map(ShopProductResponse::getId)
            .collect(java.util.stream.Collectors.joining(","));

        SessionCreateParams.Builder sessionBuilder = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.PAYMENT)
            .setCustomer(customerId)
            .setSuccessUrl(successUrl + "?checkout=success&session_id={CHECKOUT_SESSION_ID}")
            .setCancelUrl(cancelUrl + "?checkout=cancel")
            .setClientReferenceId(String.valueOf(user.getId()))
            .putMetadata("userId", String.valueOf(user.getId()))
            .putMetadata("cartCheckout", "true")
            .putMetadata("productIds", productIdsCsv);

        for (ShopProductResponse product : products) {
            SessionCreateParams.LineItem.PriceData priceData = SessionCreateParams.LineItem.PriceData.builder()
                .setCurrency(product.getCurrency())
                .setUnitAmount(product.getAmountCents())
                .setProductData(
                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                        .setName(product.getName())
                        .setDescription(product.getDescription())
                        .build()
                )
                .build();

            sessionBuilder.addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setQuantity(1L)
                    .setPriceData(priceData)
                    .build()
            );
        }

        RequestOptions requestOptions = RequestOptions.builder()
            .setIdempotencyKey(StringUtils.hasText(idempotencyKey) ? idempotencyKey : UUID.randomUUID().toString())
            .build();

        Session session = Session.create(sessionBuilder.build(), requestOptions);
        return new CreateCheckoutSessionResponse(session.getUrl(), session.getId());
    }

    @Transactional(readOnly = true)
    public List<ShopOrderResponse> getOrders(User user) {
        return shopOrderRepository.findByUserOrderByCreatedAtDesc(user).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public void syncCheckoutStatusFromSession(User user, String sessionId) throws StripeException {
        if (!StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("session_id_required");
        }

        Session session = Session.retrieve(sessionId.trim());
        if (!"payment".equalsIgnoreCase(session.getMode())) {
            throw new IllegalArgumentException("invalid_checkout_session");
        }
        if (!isCheckoutSessionOwnedByUser(session, user)) {
            throw new IllegalArgumentException("session_user_mismatch");
        }

        String nextStatus = null;
        if ("paid".equalsIgnoreCase(session.getPaymentStatus())) {
            nextStatus = STATUS_PAID;
        } else if ("expired".equalsIgnoreCase(session.getStatus())) {
            nextStatus = STATUS_EXPIRED;
        } else {
            String paymentIntentId = String.valueOf(session.getPaymentIntent());
            if (StringUtils.hasText(paymentIntentId) && !"null".equals(paymentIntentId)) {
                PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);
                if ("succeeded".equalsIgnoreCase(paymentIntent.getStatus())) {
                    nextStatus = STATUS_PAID;
                } else if ("canceled".equalsIgnoreCase(paymentIntent.getStatus())
                    || "requires_payment_method".equalsIgnoreCase(paymentIntent.getStatus())) {
                    nextStatus = STATUS_FAILED;
                }
            }
        }

        Map<String, String> metadata = session.getMetadata();
        boolean cartCheckout = metadata != null && "true".equals(metadata.get("cartCheckout"));
        if (cartCheckout) {
            if (STATUS_PAID.equals(nextStatus)) {
                createCartOrdersFromSession(session);
            }
            return;
        }

        if (nextStatus != null) {
            updateOrderFromCheckoutSession(session, nextStatus);
        }
    }

    @Transactional
    public void handleWebhook(String payload, String signatureHeader) throws SignatureVerificationException {
        if (!StringUtils.hasText(webhookSecret)) {
            throw new IllegalStateException("Stripe webhook secret is missing. Set APP_STRIPE_WEBHOOK_SECRET.");
        }

        Event event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
        if (processedStripeEventRepository.existsByEventId(event.getId())) {
            return;
        }

        String eventType = event.getType();

        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
        StripeObject rawObject;
        try {
            rawObject = dataObjectDeserializer.deserializeUnsafe();
        } catch (com.stripe.exception.EventDataObjectDeserializationException e) {
            return;
        }
        if (rawObject == null) {
            return;
        }
        Optional<StripeObject> stripeObject = Optional.of(rawObject);

        try {
            switch (eventType) {
                case "checkout.session.completed" -> {
                    if (stripeObject.get() instanceof Session session) {
                        if ("subscription".equalsIgnoreCase(session.getMode())) {
                            updateUserSubscriptionFromCheckoutSession(session);
                        }
                        Map<String, String> meta = session.getMetadata();
                        if (meta != null && "true".equals(meta.get("cartCheckout"))) {
                            createCartOrdersFromSession(session);
                        } else {
                            updateOrderFromCheckoutSession(session, STATUS_PAID);
                        }
                    }
                }
                case "checkout.session.expired" -> {
                    if (stripeObject.get() instanceof Session session) {
                        updateOrderFromCheckoutSession(session, STATUS_EXPIRED);
                    }
                }
                case "payment_intent.payment_failed" -> {
                    if (stripeObject.get() instanceof PaymentIntent paymentIntent) {
                        updateOrderFromPaymentIntent(paymentIntent, STATUS_FAILED);
                    }
                }
                case "charge.failed" -> {
                    if (stripeObject.get() instanceof Charge charge) {
                        String paymentIntentId = String.valueOf(charge.getPaymentIntent());
                        if (StringUtils.hasText(paymentIntentId) && !"null".equals(paymentIntentId)) {
                            Optional<ShopOrder> orderOpt = shopOrderRepository.findByStripePaymentIntentId(paymentIntentId);
                            orderOpt.ifPresent(order -> markStatus(order, STATUS_FAILED));
                        }
                    }
                }
                case "customer.subscription.created", "customer.subscription.updated", "customer.subscription.deleted" -> {
                    if (stripeObject.get() instanceof Subscription subscription) {
                        updateUserSubscriptionFromSubscriptionEvent(subscription);
                    }
                }
                default -> {
                    return;
                }
            }

            recordProcessedEvent(event);
        } catch (DataIntegrityViolationException ignored) {
            // duplicate delivery raced with another thread
        }
    }

    @Transactional
    public void reconcilePendingOrders() {
        List<ShopOrder> pendingOrders = shopOrderRepository.findTop100ByStatusOrderByUpdatedAtAsc(STATUS_PENDING);
        for (ShopOrder order : pendingOrders) {
            try {
                Session session = Session.retrieve(order.getStripeCheckoutSessionId());
                if ("paid".equalsIgnoreCase(session.getPaymentStatus())) {
                    markStatus(order, STATUS_PAID);
                    continue;
                }

                if ("expired".equalsIgnoreCase(session.getStatus())) {
                    markStatus(order, STATUS_EXPIRED);
                    continue;
                }

                if (StringUtils.hasText(order.getStripePaymentIntentId())) {
                    PaymentIntent paymentIntent = PaymentIntent.retrieve(order.getStripePaymentIntentId());
                    if ("succeeded".equalsIgnoreCase(paymentIntent.getStatus())) {
                        markStatus(order, STATUS_PAID);
                    } else if ("canceled".equalsIgnoreCase(paymentIntent.getStatus())
                        || "requires_payment_method".equalsIgnoreCase(paymentIntent.getStatus())) {
                        markStatus(order, STATUS_FAILED);
                    }
                }
            } catch (StripeException ignored) {
                // keep pending and retry on the next reconciliation cycle
            }
        }
    }

    private void updateOrderFromCheckoutSession(Session session, String status) {
        List<ShopOrder> orders = shopOrderRepository.findAllByStripeCheckoutSessionId(session.getId());
        for (ShopOrder order : orders) {
            order.setStripePaymentIntentId(session.getPaymentIntent());
            markStatus(order, status);
        }
    }

    private void createCartOrdersFromSession(Session session) {
        Map<String, String> meta = session.getMetadata();
        if (meta == null) return;

        String productIdsCsv = meta.get("productIds");
        String userIdStr = meta.get("userId");
        if (!StringUtils.hasText(productIdsCsv) || !StringUtils.hasText(userIdStr)) return;

        long userId;
        try {
            userId = Long.parseLong(userIdStr);
        } catch (NumberFormatException ignored) {
            return;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;

        String[] ids = productIdsCsv.split(",");
        for (String productId : ids) {
            productId = productId.trim();
            ShopProductResponse product = catalog().get(productId);
            if (product == null) continue;

            String checkoutSessionId = session.getId() + "_" + product.getId();
            if (shopOrderRepository.findByStripeCheckoutSessionId(checkoutSessionId).isPresent()) {
                continue;
            }

            ShopOrder order = new ShopOrder();
            order.setUser(user);
            order.setProductId(product.getId());
            order.setProductName(product.getName());
            order.setAmountCents(product.getAmountCents());
            order.setCurrency(product.getCurrency());
            order.setStatus(STATUS_PAID);
            order.setStripeCheckoutSessionId(checkoutSessionId);
            order.setStripePaymentIntentId(session.getPaymentIntent());
            shopOrderRepository.save(order);
            purchaseEmailService.sendOrderPaid(user, order);
            gitHubRepoService.grantRepoAccess(user.getGithubUsername(), product.getId());
        }
    }

    private void updateOrderFromPaymentIntent(PaymentIntent paymentIntent, String status) {
        Optional<ShopOrder> orderOpt = shopOrderRepository.findByStripePaymentIntentId(paymentIntent.getId());
        orderOpt.ifPresent(order -> markStatus(order, status));
    }

    private void markStatus(ShopOrder order, String nextStatus) {
        if (nextStatus.equalsIgnoreCase(order.getStatus())) {
            return;
        }

        order.setStatus(nextStatus);
        shopOrderRepository.save(order);

        if (STATUS_PAID.equals(nextStatus)) {
            purchaseEmailService.sendOrderPaid(order.getUser(), order);
            gitHubRepoService.grantRepoAccess(order.getUser().getGithubUsername(), order.getProductId());
        } else if (STATUS_FAILED.equals(nextStatus)) {
            purchaseEmailService.sendOrderFailed(order.getUser(), order);
        }
    }

    private void recordProcessedEvent(Event event) {
        ProcessedStripeEvent processed = new ProcessedStripeEvent();
        processed.setEventId(event.getId());
        processed.setEventType(event.getType());
        processed.setProcessedAt(Instant.now());
        processedStripeEventRepository.save(processed);
    }

    private boolean isCheckoutSessionOwnedByUser(Session session, User user) {
        String clientReferenceId = session.getClientReferenceId();
        if (StringUtils.hasText(clientReferenceId)) {
            try {
                if (clientReferenceId.trim().equals(String.valueOf(user.getId()))) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // fall through to metadata/customer checks
            }
        }

        Map<String, String> metadata = session.getMetadata();
        if (metadata != null) {
            String metadataUserId = metadata.get("userId");
            if (StringUtils.hasText(metadataUserId)) {
                try {
                    if (metadataUserId.trim().equals(String.valueOf(user.getId()))) {
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                    // fall through to customer checks
                }
            }
        }

        String customerId = String.valueOf(session.getCustomer());
        return StringUtils.hasText(customerId)
            && !"null".equals(customerId)
            && customerId.equals(user.getStripeCustomerId());
    }

    private String normalizeIdempotencyKey(User user, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        return "checkout:" + user.getId() + ":" + idempotencyKey.trim();
    }

    private String normalizeSubscriptionIdempotencyKey(User user, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        return "subscription:" + user.getId() + ":" + idempotencyKey.trim();
    }

    private List<ShopProductResponse> resolveScopedProducts(List<String> requestedProductIds) {
        if (requestedProductIds == null || requestedProductIds.isEmpty()) {
            return List.of();
        }

        List<String> normalizedIds = requestedProductIds.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .toList();

        if (normalizedIds.isEmpty()) {
            return List.of();
        }

        List<ShopProductResponse> scopedProducts = new ArrayList<>();
        for (String productId : normalizedIds) {
            ShopProductResponse product = catalog().get(productId);
            if (product == null) {
                throw new IllegalArgumentException("invalid_product: " + productId);
            }
            scopedProducts.add(product);
        }
        return scopedProducts;
    }

    private List<ShopProductResponse> resolveScopedProductsFromCode(ShopAccessCode accessCode) {
        if (accessCode.isFullAccess()) {
            return getProducts();
        }
        if (!StringUtils.hasText(accessCode.getProductIdsCsv())) {
            return List.of();
        }

        String[] productIds = accessCode.getProductIdsCsv().split(",");
        List<ShopProductResponse> scopedProducts = new ArrayList<>();
        for (String rawId : productIds) {
            if (!StringUtils.hasText(rawId)) {
                continue;
            }
            String productId = rawId.trim();
            ShopProductResponse product = catalog().get(productId);
            if (product != null) {
                scopedProducts.add(product);
            }
        }
        return scopedProducts;
    }

    private void requireAdminToken(String adminToken) {
        if (!StringUtils.hasText(shopAdminToken)) {
            throw new IllegalStateException("admin_token_not_configured");
        }
        if (!StringUtils.hasText(adminToken) || !shopAdminToken.equals(adminToken.trim())) {
            throw new IllegalArgumentException("invalid_admin_token");
        }
    }

    private String normalizeAccessCode(String rawCode, boolean allowBlank) {
        if (!StringUtils.hasText(rawCode)) {
            if (allowBlank) {
                return null;
            }
            throw new IllegalArgumentException("access_code_required");
        }

        String normalized = rawCode.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9-]{6,64}")) {
            throw new IllegalArgumentException("invalid_access_code_format");
        }
        return normalized;
    }

    private String generateUniqueAccessCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = "OKD-" + randomAccessCodeBlock(4) + "-" + randomAccessCodeBlock(4) + "-" + randomAccessCodeBlock(4);
            if (!shopAccessCodeRepository.existsByCode(code)) {
                return code;
            }
        }

        String fallback = "OKD-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
        if (shopAccessCodeRepository.existsByCode(fallback)) {
            throw new IllegalStateException("access_code_generation_failed");
        }
        return fallback;
    }

    private String randomAccessCodeBlock(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            int next = ACCESS_CODE_RANDOM.nextInt(ACCESS_CODE_ALPHABET.length());
            builder.append(ACCESS_CODE_ALPHABET.charAt(next));
        }
        return builder.toString();
    }

    @Transactional
    protected void updateUserSubscriptionFromCheckoutSession(Session session) {
        String customerId = String.valueOf(session.getCustomer());
        if (!StringUtils.hasText(customerId) || "null".equals(customerId)) {
            return;
        }

        Optional<User> userOpt = userRepository.findByStripeCustomerId(customerId);
        if (userOpt.isEmpty()) {
            return;
        }

        String subscriptionId = String.valueOf(session.getSubscription());
        if (StringUtils.hasText(subscriptionId) && !"null".equals(subscriptionId)) {
            User user = userOpt.get();
            user.setStripeSubscriptionId(subscriptionId);
            user.setStripeSubscriptionStatus("active");
            user.setStripeSubscriptionCancelAtPeriodEnd(false);
            user.setStripeSubscriptionCancelAt(null);
            user.setPremiumUser(true);
            userRepository.save(user);
        }
    }

    @Transactional
    protected void updateUserSubscriptionFromSubscriptionEvent(Subscription subscription) {
        String customerId = String.valueOf(subscription.getCustomer());
        if (!StringUtils.hasText(customerId) || "null".equals(customerId)) {
            return;
        }

        Optional<User> userOpt = userRepository.findByStripeCustomerId(customerId);
        if (userOpt.isEmpty()) {
            return;
        }

        User user = userOpt.get();
        String status = subscription.getStatus();
        Instant now = Instant.now();
        boolean cancelAtPeriodEnd = Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd());
        Instant cancelAt = stripeEpochToInstant(subscription.getCancelAt());
        Instant currentPeriodEnd = stripeEpochToInstant(subscription.getCurrentPeriodEnd());
        boolean premium = "active".equalsIgnoreCase(status)
            || "trialing".equalsIgnoreCase(status)
            || "past_due".equalsIgnoreCase(status)
            || (cancelAtPeriodEnd && currentPeriodEnd != null && currentPeriodEnd.isAfter(now));

        user.setStripeSubscriptionId(subscription.getId());
        user.setStripeSubscriptionStatus(status);
        user.setStripeSubscriptionCancelAtPeriodEnd(cancelAtPeriodEnd);
        user.setStripeSubscriptionCancelAt(cancelAt);
        user.setStripeSubscriptionCurrentPeriodEnd(currentPeriodEnd);
        user.setPremiumUser(premium);
        userRepository.save(user);
    }

    private Instant stripeEpochToInstant(Long epochSeconds) {
        if (epochSeconds == null) {
            return null;
        }
        return Instant.ofEpochSecond(epochSeconds);
    }

    @Transactional
    protected String ensureStripeCustomer(User user) throws StripeException {
        if (StringUtils.hasText(user.getStripeCustomerId())) {
            try {
                Customer.retrieve(user.getStripeCustomerId());
                return user.getStripeCustomerId();
            } catch (StripeException e) {
                if ("resource_missing".equals(e.getCode())) {
                    // Stale customer ID (e.g. created in test mode, now using live key).
                    // Clear it and fall through to create a fresh customer.
                    log.warn("Stripe customer {} not found in current mode ({}), creating new customer for user {}",
                            user.getStripeCustomerId(), e.getMessage(), user.getId());
                    user.setStripeCustomerId(null);
                    userRepository.save(user);
                } else {
                    throw e;
                }
            }
        }

        CustomerCreateParams params = CustomerCreateParams.builder()
            .setEmail(user.getEmail())
            .setName(user.getUsername())
            .putMetadata("userId", String.valueOf(user.getId()))
            .build();

        Customer customer = Customer.create(params);
        user.setStripeCustomerId(customer.getId());
        userRepository.save(user);
        return customer.getId();
    }

    private ShopOrderResponse toResponse(ShopOrder order) {
        ShopOrderResponse response = new ShopOrderResponse();
        response.setId(order.getId());
        response.setProductId(order.getProductId());
        response.setProductName(order.getProductName());
        response.setAmountCents(order.getAmountCents());
        response.setCurrency(order.getCurrency());
        response.setStatus(order.getStatus());
        response.setStripeCheckoutSessionId(order.getStripeCheckoutSessionId());
        response.setStripePaymentIntentId(order.getStripePaymentIntentId());
        response.setCreatedAt(order.getCreatedAt());
        response.setUpdatedAt(order.getUpdatedAt());
        return response;
    }

    private Map<String, ShopProductResponse> catalog() {
        Map<String, ShopProductResponse> products = new LinkedHashMap<>();
        products.put("keycard-crates", new ShopProductResponse(
            "keycard-crates",
            "KeyCard Crates",
            "Automated crate spawning, keycard-locked doors, dynamic loot events, and no-code config for DayZ.",
            40000,
            currency
        ));
        products.put("weapon-system", new ShopProductResponse(
            "weapon-system",
            "Weapon System",
            "150+ attachments, 100+ firearms, true part-on-part modularity, custom optics, and deployable bipods for DayZ.",
            40000,
            currency
        ));
        products.put("battle-pass", new ShopProductResponse(
            "battle-pass",
            "Battle Pass",
            "XP-based tier progression, weapon mastery, free and paid reward tracks — fully Bohemia-compliant.",
            40000,
            currency
        ));
        return products;
    }
}
