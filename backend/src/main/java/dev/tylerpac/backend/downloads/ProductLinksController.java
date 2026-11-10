package dev.tylerpac.backend.downloads;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Public: each product's GitHub link for its page ({"keycard-crates": "https://github.com/..."}).
@RestController
public class ProductLinksController {

    private final ProductLinkRepository links;

    public ProductLinksController(ProductLinkRepository links) {
        this.links = links;
    }

    @GetMapping("/shop/product-links")
    public Map<String, String> links() {
        Map<String, String> result = new LinkedHashMap<>();
        links.findAll().forEach(link -> result.put(link.getProductId(), link.getGithubUrl()));
        return result;
    }
}
