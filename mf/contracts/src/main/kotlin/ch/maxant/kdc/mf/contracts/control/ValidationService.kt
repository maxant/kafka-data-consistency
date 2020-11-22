package ch.maxant.kdc.mf.contracts.control

import ch.maxant.kdc.mf.contracts.adapter.pricing.PricingService
import java.util.*
import javax.enterprise.context.Dependent
import javax.inject.Inject
import javax.validation.ValidationException

@Dependent
class ValidationService(
    @Inject
    @RestClient
    val pricingService: PricingService
){
    /**
     * @throws PricingValidationException if the prices are not in sync
     */
    fun validateContractIsInSync(contractId: UUID, syncTimestamp: Long) {
        if(pricingService.validateSyncTime(contractId, syncTimestamp) != 0) {
            throw PricingValidationException("Pricing is not in sync with the contract $contractId and timestamp $syncTimestamp")
        }
    }
}

class PricingValidationException(msg: String): ValidationException(msg)