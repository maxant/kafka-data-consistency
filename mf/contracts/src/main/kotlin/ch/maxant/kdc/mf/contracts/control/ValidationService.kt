package ch.maxant.kdc.mf.contracts.control

import ch.maxant.kdc.mf.contracts.adapter.PartnerAdapter
import ch.maxant.kdc.mf.contracts.adapter.PricingAdapter
import org.eclipse.microprofile.rest.client.inject.RestClient
import java.util.*
import javax.enterprise.context.Dependent
import javax.inject.Inject
import javax.validation.ValidationException

@Dependent
class ValidationService {

    @Inject
    @RestClient // bizarrely this doesnt work with constructor injection
    lateinit var pricingAdapter: PricingAdapter

    @Inject
    @RestClient // bizarrely this doesnt work with constructor injection
    lateinit var partnerAdapter: PartnerAdapter

    /**
     * @throws PricingValidationException if the prices are not in sync
     */
    fun validateContractIsInSync(contractId: UUID, syncTimestamp: Long) {
        if(pricingAdapter.countNotSameSyncTime(contractId, syncTimestamp) != 0) {
            throw PricingValidationException("Pricing is not in sync with the contract $contractId and timestamp $syncTimestamp")
        }

        // partners may already exist beforehand and they are loosely coupled to the sales system,
        // so it doesnt make sense to use a syncTimestamp for them => just check existance

        partnerAdapter.validate(contractId)
    }
}

class PricingValidationException(msg: String): ValidationException(msg)
