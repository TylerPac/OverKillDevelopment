package dev.tylerpac.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateCheckoutSessionRequest {

    @NotBlank(message = "product_id_required")
    private String productId;

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }
}
