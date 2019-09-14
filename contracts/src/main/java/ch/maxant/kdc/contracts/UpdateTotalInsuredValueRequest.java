package ch.maxant.kdc.contracts;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class UpdateTotalInsuredValueRequest {

    private String contractNumber;
    private LocalDateTime from;
    private BigDecimal newTotalInsuredValue;

    public String getContractNumber() {
        return contractNumber;
    }

    public void setContractNumber(String contractNumber) {
        this.contractNumber = contractNumber;
    }

    public BigDecimal getNewTotalInsuredValue() {
        return newTotalInsuredValue;
    }

    public void setNewTotalInsuredValue(BigDecimal newTotalInsuredValue) {
        this.newTotalInsuredValue = newTotalInsuredValue;
    }

    public LocalDateTime getFrom() {
        return from;
    }

    public void setFrom(LocalDateTime from) {
        this.from = from;
    }
}
