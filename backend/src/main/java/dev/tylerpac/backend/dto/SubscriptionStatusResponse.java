package dev.tylerpac.backend.dto;

import java.time.Instant;

public class SubscriptionStatusResponse {

    private boolean premiumUser;
    private String subscriptionStatus;
    private String stripeSubscriptionId;
    private boolean cancelAtPeriodEnd;
    private Instant cancelAt;
    private Instant currentPeriodEnd;
    private boolean emailVerified;
    private boolean accountSetupComplete;

    public SubscriptionStatusResponse() {}

    public SubscriptionStatusResponse(boolean premiumUser, String subscriptionStatus) {
        this.premiumUser = premiumUser;
        this.subscriptionStatus = subscriptionStatus;
    }

    public SubscriptionStatusResponse(boolean premiumUser, String subscriptionStatus, boolean emailVerified, boolean accountSetupComplete) {
        this.premiumUser = premiumUser;
        this.subscriptionStatus = subscriptionStatus;
        this.emailVerified = emailVerified;
        this.accountSetupComplete = accountSetupComplete;
    }

    public SubscriptionStatusResponse(boolean premiumUser, String subscriptionStatus, String stripeSubscriptionId, boolean emailVerified, boolean accountSetupComplete) {
        this.premiumUser = premiumUser;
        this.subscriptionStatus = subscriptionStatus;
        this.stripeSubscriptionId = stripeSubscriptionId;
        this.emailVerified = emailVerified;
        this.accountSetupComplete = accountSetupComplete;
    }

    public SubscriptionStatusResponse(
        boolean premiumUser,
        String subscriptionStatus,
        String stripeSubscriptionId,
        boolean cancelAtPeriodEnd,
        Instant cancelAt,
        Instant currentPeriodEnd,
        boolean emailVerified,
        boolean accountSetupComplete
    ) {
        this.premiumUser = premiumUser;
        this.subscriptionStatus = subscriptionStatus;
        this.stripeSubscriptionId = stripeSubscriptionId;
        this.cancelAtPeriodEnd = cancelAtPeriodEnd;
        this.cancelAt = cancelAt;
        this.currentPeriodEnd = currentPeriodEnd;
        this.emailVerified = emailVerified;
        this.accountSetupComplete = accountSetupComplete;
    }

    public boolean isPremiumUser() {
        return premiumUser;
    }

    public void setPremiumUser(boolean premiumUser) {
        this.premiumUser = premiumUser;
    }

    public String getSubscriptionStatus() {
        return subscriptionStatus;
    }

    public void setSubscriptionStatus(String subscriptionStatus) {
        this.subscriptionStatus = subscriptionStatus;
    }

    public String getStripeSubscriptionId() {
        return stripeSubscriptionId;
    }

    public void setStripeSubscriptionId(String stripeSubscriptionId) {
        this.stripeSubscriptionId = stripeSubscriptionId;
    }

    public boolean isCancelAtPeriodEnd() {
        return cancelAtPeriodEnd;
    }

    public void setCancelAtPeriodEnd(boolean cancelAtPeriodEnd) {
        this.cancelAtPeriodEnd = cancelAtPeriodEnd;
    }

    public Instant getCancelAt() {
        return cancelAt;
    }

    public void setCancelAt(Instant cancelAt) {
        this.cancelAt = cancelAt;
    }

    public Instant getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public void setCurrentPeriodEnd(Instant currentPeriodEnd) {
        this.currentPeriodEnd = currentPeriodEnd;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public boolean isAccountSetupComplete() {
        return accountSetupComplete;
    }

    public void setAccountSetupComplete(boolean accountSetupComplete) {
        this.accountSetupComplete = accountSetupComplete;
    }
}
