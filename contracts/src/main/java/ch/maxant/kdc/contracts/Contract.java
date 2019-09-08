package ch.maxant.kdc.contracts;

import ch.maxant.kdc.products.Product;
import org.hibernate.annotations.Type;
import org.hibernate.envers.Audited;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Audited
@Entity
@Table(name = "CONTRACTS")
public class Contract {

    @Id
    @Column(name = "ID")
    @Type(type="uuid-char")
    private UUID id = UUID.randomUUID();

    @Column(updatable = false, nullable = false, name = "CONTRACTNUMBER")
    private String contractNumber;

    @Version
    @Column(nullable = false, name = "VERSION")
    private Integer version;

    @Column(nullable = false, name = "FROM_")
    private LocalDateTime from;

    @Column(nullable = false, name = "TO_")
    private LocalDateTime to;

    @OneToOne(fetch = FetchType.LAZY, optional = false) // optional false forces lazy. without it, jpa is generating extra sql to load the product
    @JoinColumn(name = "PRODUCT_ID", referencedColumnName = "ID")
    private Product product;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getContractNumber() {
        return contractNumber;
    }

    public void setContractNumber(String contractNumber) {
        this.contractNumber = contractNumber;
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

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }
}
