package dev.tylerpac.backend.downloads;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// The GitHub link shown on a product's page, set from the admin page.
@Entity
@Table(name = "product_links")
public class ProductLink {

    @Id
    @Column(length = 64)
    private String productId;

    @Column(nullable = false, length = 300)
    private String githubUrl;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ProductLink() {
    }

    public ProductLink(String productId, String githubUrl) {
        this.productId = productId;
        update(githubUrl);
    }

    public void update(String githubUrl) {
        this.githubUrl = githubUrl;
        this.updatedAt = Instant.now();
    }

    public String getProductId() {
        return productId;
    }

    public String getGithubUrl() {
        return githubUrl;
    }
}
