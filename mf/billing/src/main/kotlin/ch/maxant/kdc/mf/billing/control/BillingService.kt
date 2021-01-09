package ch.maxant.kdc.mf.billing.control

import ch.maxant.kdc.mf.billing.boundary.Group
import ch.maxant.kdc.mf.billing.definitions.ProductId
import ch.maxant.kdc.mf.billing.entity.BillsEntity
import ch.maxant.kdc.mf.library.MessageBuilder
import com.fasterxml.jackson.databind.ObjectMapper
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import java.time.LocalDateTime
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.persistence.EntityManager
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

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
            }
        }
    }
}

