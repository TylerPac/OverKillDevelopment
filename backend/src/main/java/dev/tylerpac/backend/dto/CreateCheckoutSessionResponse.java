package dev.tylerpac.backend.dto;

public class CreateCheckoutSessionResponse {

    private String checkoutUrl;
    private String sessionId;

    public CreateCheckoutSessionResponse() {}

    public CreateCheckoutSessionResponse(String checkoutUrl, String sessionId) {
        this.checkoutUrl = checkoutUrl;
        this.sessionId = sessionId;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public void setCheckoutUrl(String checkoutUrl) {
        this.checkoutUrl = checkoutUrl;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
