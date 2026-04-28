package dev.tylerpac.backend.dto;

import java.util.ArrayList;
import java.util.List;

public class RedeemFullAccessCodeResponse {

    private int grantedCount;
    private List<String> grantedProductIds = new ArrayList<>();

    public RedeemFullAccessCodeResponse() {
    }

    public RedeemFullAccessCodeResponse(int grantedCount, List<String> grantedProductIds) {
        this.grantedCount = grantedCount;
        this.grantedProductIds = grantedProductIds;
    }

    public int getGrantedCount() {
        return grantedCount;
    }

    public void setGrantedCount(int grantedCount) {
        this.grantedCount = grantedCount;
    }

    public List<String> getGrantedProductIds() {
        return grantedProductIds;
    }

    public void setGrantedProductIds(List<String> grantedProductIds) {
        this.grantedProductIds = grantedProductIds;
    }
}