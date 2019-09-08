package ch.maxant.kdc.products;

import org.hibernate.envers.Audited;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.math.BigDecimal;

@Audited
@Entity
@Table(name = "HOME_CONTENTS_PRODUCTS")
public class HomeContentsInsurance extends Product implements WithTotalInsuredValue {

    public HomeContentsInsurance() {
        setName(getClass().getSimpleName());
    }

    @Column(nullable = false, name = "TOTAL_INSURED_VALUE")
    private BigDecimal totalInsuredValue;

    public BigDecimal getTotalInsuredValue() {
        return totalInsuredValue;
    }

    public void setTotalInsuredValue(BigDecimal totalInsuredValue) {
        this.totalInsuredValue = totalInsuredValue;
    }
}
