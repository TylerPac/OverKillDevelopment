package dev.tylerpac.backend.dto;

public class ShopProductResponse {

    private String id;
    private String name;
    private String description;
    private long amountCents;
    private String currency;

    public ShopProductResponse() {}

    public ShopProductResponse(String id, String name, String description, long amountCents, String currency) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.amountCents = amountCents;
        this.currency = currency;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public void setAmountCents(long amountCents) {
        this.amountCents = amountCents;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
