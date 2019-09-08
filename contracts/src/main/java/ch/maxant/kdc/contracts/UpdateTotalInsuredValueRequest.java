package ch.maxant.kdc.contracts;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class UpdateTotalInsuredValueRequest {

    private UUID contractId;
    private LocalDateTime from;
    private BigDecimal newTotalInsuredValue;

    public UUID getContractId() {
        return contractId;
    }

    public void setContractId(UUID contractId) {
        this.contractId = contractId;
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
