package ch.maxant.kdc.claims;

import java.util.UUID;

public class Claim {

    private String id = UUID.randomUUID().toString();
    private String description;
    private String customerId;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
