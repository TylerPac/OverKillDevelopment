package dev.tylerpac.backend.dayz.dto;

/** Base for POST bodies: DayZ's RestContext cannot send custom headers, so server credentials ride in the body. */
public class DayzAuthenticatedRequest {

    private String serverId;
    private String apiKey;

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
