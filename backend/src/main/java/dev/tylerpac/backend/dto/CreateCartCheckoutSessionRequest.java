package dev.tylerpac.backend.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public class CreateCartCheckoutSessionRequest {

    @NotEmpty(message = "product_ids_required")
    @Size(max = 20, message = "too_many_items")
    private List<String> productIds;

    public List<String> getProductIds() {
        return productIds;
    }

    public void setProductIds(List<String> productIds) {
        this.productIds = productIds;
    }
}
