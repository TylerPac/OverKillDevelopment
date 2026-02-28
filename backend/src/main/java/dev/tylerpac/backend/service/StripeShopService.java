package dev.tylerpac.backend.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;

import dev.tylerpac.backend.dto.CreateCheckoutSessionResponse;
import dev.tylerpac.backend.dto.ShopOrderResponse;
import dev.tylerpac.backend.dto.ShopProductResponse;
import dev.tylerpac.backend.model.ProcessedStripeEvent;
import dev.tylerpac.backend.model.ShopOrder;
import dev.tylerpac.backend.model.User;
import dev.tylerpac.backend.repo.ProcessedStripeEventRepository;
import dev.tylerpac.backend.repo.ShopOrderRepository;
import dev.tylerpac.backend.repo.UserRepository;

@Service
public class StripeShopService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PAID = "PAID";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_EXPIRED = "EXPIRED";

    private final ShopOrderRepository shopOrderRepository;
    private final ProcessedStripeEventRepository processedStripeEventRepository;
    private final PurchaseEmailService purchaseEmailService;
    private final UserRepository userRepository;
    private final String currency;
    private final String successUrl;
    private final String cancelUrl;
    private final String webhookSecret;

    public StripeShopService(
        ShopOrderRepository shopOrderRepository,
        ProcessedStripeEventRepository processedStripeEventRepository,
        PurchaseEmailService purchaseEmailService,
        UserRepository userRepository,
        @Value("${app.shop.currency:usd}") String currency,
        @Value("${app.shop.success-url}") String successUrl,
        @Value("${app.shop.cancel-url}") String cancelUrl,
        @Value("${app.stripe.secret-key:}") String stripeSecretKey,
        @Value("${app.stripe.webhook-secret:}") String webhookSecret
    ) {
        this.shopOrderRepository = shopOrderRepository;
        this.processedStripeEventRepository = processedStripeEventRepository;
        this.purchaseEmailService = purchaseEmailService;
        this.userRepository = userRepository;
        this.currency = currency;
        this.successUrl = successUrl;
        this.cancelUrl = cancelUrl;
        this.webhookSecret = webhookSecret;

        if (!StringUtils.hasText(stripeSecretKey)) {
            throw new IllegalStateException("Stripe secret key is missing. Set APP_STRIPE_SECRET_KEY.");
        }
        Stripe.apiKey = stripeSecretKey;
    }

    public List<ShopProductResponse> getProducts() {
        return List.copyOf(catalog().values());
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

    @Transactional(readOnly = true)
    public List<ShopOrderResponse> getOrders(User user) {
        return shopOrderRepository.findByUserOrderByCreatedAtDesc(user).stream()
            .map(this::toResponse)
            .toList();
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
        Optional<StripeObject> stripeObject = dataObjectDeserializer.getObject();
        if (stripeObject.isEmpty()) {
            return;
        }

        try {
            switch (eventType) {
                case "checkout.session.completed" -> {
                    if (stripeObject.get() instanceof Session session) {
                        updateOrderFromCheckoutSession(session, STATUS_PAID);
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
        Optional<ShopOrder> orderOpt = shopOrderRepository.findByStripeCheckoutSessionId(session.getId());
        if (orderOpt.isPresent()) {
            ShopOrder order = orderOpt.get();
            order.setStripePaymentIntentId(session.getPaymentIntent());
            markStatus(order, status);
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

    private String normalizeIdempotencyKey(User user, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        return "checkout:" + user.getId() + ":" + idempotencyKey.trim();
    }

    @Transactional
    protected String ensureStripeCustomer(User user) throws StripeException {
        if (StringUtils.hasText(user.getStripeCustomerId())) {
            return user.getStripeCustomerId();
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
            "Keycard and Dynamic Crate Mod for DayZ",
            10000,
            currency
        ));
        products.put("pro-pack", new ShopProductResponse(
            "pro-pack",
            "Pro hello",
            "Expanded assets + premium templates",
            4900,
            currency
        ));
        products.put("studio-pack", new ShopProductResponse(
            "studio-pack",
            "Studio Pack",
            "Full bundle with lifetime updates",
            9900,
            currency
        ));
        return products;
    }
}
