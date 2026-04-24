package dev.tylerpac.backend.dto;

public class AuthResponse {
    private String token;
    private String refreshToken;
    private String tokenType;
    private long expiresInSeconds;
    private boolean premiumUser;
    private String subscriptionStatus;
    private boolean emailVerified;
    private boolean accountSetupComplete;

    public AuthResponse() {}

    public AuthResponse(String token) {
        this.token = token;
    }

    public AuthResponse(String token, String refreshToken, String tokenType, long expiresInSeconds) {
        this.token = token;
        this.refreshToken = refreshToken;
        this.tokenType = tokenType;
        this.expiresInSeconds = expiresInSeconds;
    }

    public AuthResponse(String token, String refreshToken, String tokenType, long expiresInSeconds, boolean premiumUser, String subscriptionStatus) {
        this.token = token;
        this.refreshToken = refreshToken;
        this.tokenType = tokenType;
        this.expiresInSeconds = expiresInSeconds;
        this.premiumUser = premiumUser;
        this.subscriptionStatus = subscriptionStatus;
    }

    public AuthResponse(
        String token,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        boolean premiumUser,
        String subscriptionStatus,
        boolean emailVerified,
        boolean accountSetupComplete
    ) {
        this.token = token;
        this.refreshToken = refreshToken;
        this.tokenType = tokenType;
        this.expiresInSeconds = expiresInSeconds;
        this.premiumUser = premiumUser;
        this.subscriptionStatus = subscriptionStatus;
        this.emailVerified = emailVerified;
        this.accountSetupComplete = accountSetupComplete;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(long expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
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
