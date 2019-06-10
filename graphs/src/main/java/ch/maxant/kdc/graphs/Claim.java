package ch.maxant.kdc.graphs;

import java.math.BigDecimal;
import java.util.UUID;

public class Claim {

    private String id = UUID.randomUUID().toString();
    private String partnerId;
    private String date;
    private BigDecimal reserve; // how much money to reservce for this claim; what is likely to still be claimed

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

    public String getPartnerId() {
        return partnerId;
    }

    public void setPartnerId(String partnerId) {
        this.partnerId = partnerId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

}
