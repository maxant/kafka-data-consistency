package ch.maxant.kdc.products;

import ch.maxant.kdc.contracts.entity.Contract;
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
public class Product implements WithValidity {

    @Id
    @Column(name = "ID")
    @Type(type="uuid-char")
    private UUID id = UUID.randomUUID();

    @Column(name = "CONTRACT_ID")
    @Type(type="uuid-char")
    private UUID contractId;

    @Version
    @Column(nullable = false, name = "VERSION")
    private Integer version;

    @Column(nullable = false, name = "FROM_")
    private LocalDateTime from;

    @Column(nullable = false, name = "TO_")
    private LocalDateTime to;

    @Column(nullable = false, name = "DISCOUNT")
    private BigDecimal discount;

    @Column(nullable = false, name = "INSURED_SUM")
    private BigDecimal insuredSum;

    @Column(nullable = false, updatable = false, name = "NAME")
    private String name;

    /** optionally set depending on call made */
    @Transient
    private Contract contract;

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

    public UUID getContractId() {
        return contractId;
    }

    public void setContractId(UUID contractId) {
        this.contractId = contractId;
    }

    public Contract getContract() {
        return contract;
    }

    public void setContract(Contract contract) {
        this.contract = contract;
    }

    public BigDecimal getInsuredSum() {
        return insuredSum;
    }

    public void setInsuredSum(BigDecimal insuredSum) {
        this.insuredSum = insuredSum;
    }

    public UUID getId() {
        return id;
    }

    @Override
    public String toString() {
        return "Product{" +
                "id=" + id +
                ", contractId=" + contractId +
                ", version=" + version +
                ", from=" + from +
                ", to=" + to +
                ", discount=" + discount +
                ", insuredSum=" + insuredSum +
                ", name='" + name + '\'' +
                '}';
    }
}
