package dev.tylerpac.backend.dto;

import java.util.List;

import jakarta.validation.constraints.Size;

public class CreateFullAccessCodeRequest {

    @Size(max = 64, message = "invalid_access_code_format")
    private String code;

    @Size(max = 20, message = "too_many_items")
    private List<String> productIds;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public List<String> getProductIds() {
        return productIds;
    }

    public void setProductIds(List<String> productIds) {
        this.productIds = productIds;
    }
}