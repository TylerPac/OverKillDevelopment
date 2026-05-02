package dev.tylerpac.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.stripe.exception.AuthenticationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Balance;

/**
 * Validates Stripe configuration at startup.
 * Fails fast with a clear error if keys are missing or invalid,
 * so a broken Stripe config is caught before the app accepts traffic.
 */
@Component
public class StripeStartupValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StripeStartupValidator.class);

    private final String webhookSecret;

    public StripeStartupValidator(
            @Value("${app.stripe.webhook-secret:}") String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    @Override
    public void run(ApplicationArguments args) {
        validateWebhookSecret();
        validateStripeApiKey();
    }

    private void validateWebhookSecret() {
        if (!StringUtils.hasText(webhookSecret)) {
            throw new IllegalStateException(
                "[Stripe] STRIPE_WEBHOOK_SECRET is not set. " +
                "Set APP_STRIPE_WEBHOOK_SECRET (whsec_...) in your environment."
            );
        }
        if (!webhookSecret.startsWith("whsec_")) {
            throw new IllegalStateException(
                "[Stripe] STRIPE_WEBHOOK_SECRET has an invalid format: '" +
                webhookSecret.substring(0, Math.min(12, webhookSecret.length())) + "...'. " +
                "Expected a value starting with 'whsec_'. " +
                "Find it at: Stripe Dashboard → Developers → Webhooks → your endpoint → Signing secret."
            );
        }
        log.info("[Stripe] Webhook secret format OK (whsec_...)");
    }

    private void validateStripeApiKey() {
        // Stripe.apiKey is set in StripeShopService constructor — it must be set before this runs.
        // We call Balance.retrieve() as a lightweight live API check.
        try {
            Balance.retrieve();
            log.info("[Stripe] API key is valid — live Stripe connection confirmed.");
        } catch (AuthenticationException e) {
            throw new IllegalStateException(
                "[Stripe] API key is invalid or revoked. Stripe returned: " + e.getMessage() +
                ". Update STRIPE_SECRET_KEY in your environment with a valid sk_live_... or sk_test_... key."
            );
        } catch (StripeException e) {
            // Other Stripe errors (rate limit, network) are non-fatal — don't block startup.
            log.warn("[Stripe] Could not verify API key at startup (non-fatal): {} — {}", e.getCode(), e.getMessage());
        }
    }
}
