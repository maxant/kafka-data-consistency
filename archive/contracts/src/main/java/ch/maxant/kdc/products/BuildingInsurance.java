package ch.maxant.kdc.products;

import org.hibernate.envers.Audited;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.math.BigDecimal;

@Audited
@Entity
@Table(name = "BUILDING_PRODUCTS")
public class BuildingInsurance extends Product implements WithIndexValue {

    public BuildingInsurance() {
        setName(getClass().getSimpleName());
    }

    @Column(nullable = false, name = "INDEX_VALUE")
    private BigDecimal indexValue;

    public BigDecimal getIndexValue() {
        return indexValue;
    }

    public void setIndexValue(BigDecimal indexValue) {
        this.indexValue = indexValue;
    }
}
