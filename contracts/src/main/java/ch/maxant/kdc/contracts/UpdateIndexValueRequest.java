package ch.maxant.kdc.contracts;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class UpdateIndexValueRequest {

    private String contractNumber;
    private LocalDateTime from;
    private BigDecimal newIndexValue;

    public String getContractNumber() {
        return contractNumber;
    }

    public void setContractNumber(String contractNumber) {
        this.contractNumber = contractNumber;
    }

    public BigDecimal getNewIndexValue() {
        return newIndexValue;
    }

    public void setNewIndexValue(BigDecimal newIndexValue) {
        this.newIndexValue = newIndexValue;
    }

    public LocalDateTime getFrom() {
        return from;
    }

    public void setFrom(LocalDateTime from) {
        this.from = from;
    }
}
