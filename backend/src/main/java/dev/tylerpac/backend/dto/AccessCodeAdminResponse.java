package dev.tylerpac.backend.dto;

import java.util.List;

public class AccessCodeAdminResponse {

    private Long id;
    private String code;
    private boolean fullAccess;
    private List<String> productIds;
    private boolean redeemed;
    private String redeemedByUsername;
    private String redeemedAt;
    private boolean revoked;
    private String createdAt;

    public AccessCodeAdminResponse() {}

    public AccessCodeAdminResponse(Long id, String code, boolean fullAccess, List<String> productIds,
                                   boolean redeemed, String redeemedByUsername, String redeemedAt,
                                   boolean revoked, String createdAt) {
        this.id = id;
        this.code = code;
        this.fullAccess = fullAccess;
        this.productIds = productIds;
        this.redeemed = redeemed;
        this.redeemedByUsername = redeemedByUsername;
        this.redeemedAt = redeemedAt;
        this.revoked = revoked;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public boolean isFullAccess() { return fullAccess; }
    public void setFullAccess(boolean fullAccess) { this.fullAccess = fullAccess; }

    public List<String> getProductIds() { return productIds; }
    public void setProductIds(List<String> productIds) { this.productIds = productIds; }

    public boolean isRedeemed() { return redeemed; }
    public void setRedeemed(boolean redeemed) { this.redeemed = redeemed; }

    public String getRedeemedByUsername() { return redeemedByUsername; }
    public void setRedeemedByUsername(String redeemedByUsername) { this.redeemedByUsername = redeemedByUsername; }

    public String getRedeemedAt() { return redeemedAt; }
    public void setRedeemedAt(String redeemedAt) { this.redeemedAt = redeemedAt; }

    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
