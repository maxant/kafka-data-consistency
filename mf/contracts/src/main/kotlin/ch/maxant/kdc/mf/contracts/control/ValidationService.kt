package ch.maxant.kdc.mf.contracts.control

import ch.maxant.kdc.mf.contracts.adapter.PartnerRelationshipsAdapter
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
    lateinit var partnerRelationshipsAdapter: PartnerRelationshipsAdapter

    /**
     * Throws PricingValidationException if the prices are not in sync, or others if there are problems with
     * partner relationships
     */
    fun validateContractIsInSyncToOfferIt(contractId: UUID, syncTimestamp: Long) {
        if(pricingAdapter.countNotSameSyncTime(contractId, syncTimestamp) != 0) {
            throw PricingValidationException("Pricing is not in sync with the contract $contractId and timestamp $syncTimestamp")
        }

        // partners may already exist beforehand and they are loosely coupled to the sales system,
        // so it doesnt make sense to use a syncTimestamp for them => just check existance

        partnerRelationshipsAdapter.validate(contractId, listOf("INVOICE_RECIPIENT"))
    }
}

class PricingValidationException(msg: String): ValidationException(msg)
