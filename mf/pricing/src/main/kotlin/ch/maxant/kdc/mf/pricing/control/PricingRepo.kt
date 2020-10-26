package ch.maxant.kdc.mf.pricing.control

import ch.maxant.kdc.mf.pricing.dto.Component
import java.util.*
import javax.enterprise.context.Dependent
import javax.inject.Inject
import javax.persistence.EntityManager

@Dependent
class PricingRepo(
        @Inject
        var em: EntityManager
){
    fun updatePrices(contractId: UUID, components: Set<Component>) {
        TODO()
    }
}
