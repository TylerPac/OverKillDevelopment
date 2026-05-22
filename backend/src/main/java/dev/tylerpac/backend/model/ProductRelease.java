package dev.tylerpac.backend.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_releases")
public class ProductRelease {

    @Id
    private String productId;

    @Column(nullable = false)
    private Instant releasedAt;

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public Instant getReleasedAt() { return releasedAt; }
    public void setReleasedAt(Instant releasedAt) { this.releasedAt = releasedAt; }
}
