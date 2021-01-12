package ch.maxant.kdc.mf.billing.control

import ch.maxant.kdc.mf.billing.boundary.Group
import ch.maxant.kdc.mf.billing.entity.BillsEntity
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.persistence.EntityManager

@ApplicationScoped
class BillingService {

    @Inject
    lateinit var em: EntityManager

    fun billGroup(group: Group) {
        group.contracts.forEach { contract ->
            contract.periodsToBill.forEach { period ->
                val bill = BillsEntity(UUID.randomUUID(), contract.contractId,
                        contract.billingDefinitionId, period.from.atStartOfDay(), period.to.atStartOfDay(), period.price)
                em.persist(bill)
                period.billId = bill.id
            }
        }
    }
}

