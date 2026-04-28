package dev.tylerpac.backend.dto;

import java.util.ArrayList;
import java.util.List;

public class CreateFullAccessCodeResponse {

    private String code;
    private boolean singleUse;
    private boolean fullAccess;
    private List<String> productIds = new ArrayList<>();
    private int productCount;

    public CreateFullAccessCodeResponse() {
    }

    public CreateFullAccessCodeResponse(String code, boolean singleUse, boolean fullAccess, List<String> productIds, int productCount) {
        this.code = code;
        this.singleUse = singleUse;
        this.fullAccess = fullAccess;
        this.productIds = productIds;
        this.productCount = productCount;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public boolean isSingleUse() {
        return singleUse;
    }

    public void setSingleUse(boolean singleUse) {
        this.singleUse = singleUse;
    }

    public boolean isFullAccess() {
        return fullAccess;
    }

    public void setFullAccess(boolean fullAccess) {
        this.fullAccess = fullAccess;
    }

    public List<String> getProductIds() {
        return productIds;
    }

    public void setProductIds(List<String> productIds) {
        this.productIds = productIds;
    }

    public int getProductCount() {
        return productCount;
    }

    public void setProductCount(int productCount) {
        this.productCount = productCount;
    }
}