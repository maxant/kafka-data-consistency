package ch.maxant.kdc.claims;

import java.math.BigDecimal;
import java.util.UUID;

public class Claim {

    private String id = UUID.randomUUID().toString();
    private String summary;
    private String description;
    private String customerId;
    private String date; // TODO convert to localdate - needs objectMapper to be compatible - see other github examples
    private BigDecimal reserve; // how much money to reservce for this claim; what is likely to still be claimed
    private Location location;

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public BigDecimal getReserve() {
        return reserve;
    }

    public void setReserve(BigDecimal reserve) {
        this.reserve = reserve;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

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

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }
}
