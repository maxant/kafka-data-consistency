package ch.maxant.kdc.products;

import org.hibernate.annotations.Type;
import org.hibernate.envers.Audited;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Audited
@Entity
@Table(name = "PRODUCTS")
@Inheritance(strategy = InheritanceType.JOINED)
public class Product {

    @Id
    @Column(name = "ID")
    @Type(type="uuid-char")
    private UUID id = UUID.randomUUID();

    @Version
    @Column(nullable = false, name = "VERSION")
    private Integer version;

    @Column(nullable = false, name = "FROM_")
    private LocalDateTime from;

    @Column(nullable = false, name = "TO_")
    private LocalDateTime to;

    @Column(nullable = false, name = "DISCOUNT")
    private BigDecimal discount;

    @Column(nullable = false, updatable = false, name = "NAME")
    private String name;

    public void setId(UUID id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public LocalDateTime getFrom() {
        return from;
    }

    public void setFrom(LocalDateTime from) {
        this.from = from;
    }

    public LocalDateTime getTo() {
        return to;
    }

    public void setTo(LocalDateTime to) {
        this.to = to;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public void setDiscount(BigDecimal discount) {
        this.discount = discount;
    }

    public String getName() {
        return name;
    }

    protected void setName(String name) {
        this.name = name;
    }
}
