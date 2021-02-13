package ch.maxant.kdc.mf.partners.control

import ch.maxant.kdc.mf.library.MessageBuilder
import ch.maxant.kdc.mf.partners.adapter.OrganisationAdapter
import ch.maxant.kdc.mf.partners.boundary.CreatePartnerRelationshipCommand
import ch.maxant.kdc.mf.partners.entity.AddressEntity
import ch.maxant.kdc.mf.partners.entity.AddressType
import ch.maxant.kdc.mf.partners.entity.PartnerRelationshipEntity
import ch.maxant.kdc.mf.partners.entity.Role
import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Timed
import org.eclipse.microprofile.opentracing.Traced
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.jboss.logging.Logger
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.Observes
import javax.enterprise.event.TransactionPhase
import javax.inject.Inject
import javax.persistence.EntityManager
import javax.transaction.Transactional
import javax.validation.ValidationException

@ApplicationScoped
@SuppressWarnings("unused")
@Traced
class PartnerService(
        @Inject
        var em: EntityManager,

        @Inject
        var messageBuilder: MessageBuilder
) {
    @Inject
    @RestClient // bizarrely this doesnt work with constructor injection
    lateinit var organisationAdapter: OrganisationAdapter

    @Inject // this doesnt appear to work in the constructor
    @Channel("partners-out")
    lateinit var partnersOut: Emitter<String>

    @Inject
    private lateinit var partnerRelationshipEvent: javax.enterprise.event.Event<PartnerRelationshipEntity>

    private val log = Logger.getLogger(this.javaClass)

    @Transactional
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun createRelationship(command: CreatePartnerRelationshipCommand) {
        log.info("creating a partner relationship: $command")

        val relationships = mutableListOf<PartnerRelationshipEntity>()

        relationships.add(
                PartnerRelationshipEntity(UUID.randomUUID(), command.partnerId, command.foreignId, command.start,
                                                                                        command.end, command.role))
        command.additionalRelationshipsToCreate.forEach {
            if(it == Role.SALES_REP) {
                log.info("adding sales rep relationship")
                val primaryAddress = AddressEntity.Queries
                        .selectByPartnerId(em, command.partnerId)
                        .find { address -> address.type == AddressType.PRIMARY }
                        ?: throw PartnerHasNoPrimaryAddressValidationException(
                                "Partner ${command.partnerId} has no primary address " +
                                        "and so no sales rep can be selected")

                val salesRep = organisationAdapter.getStaffByRoleAndPostCode(Role.SALES_REP, primaryAddress.postcode)

                relationships.add(
                    PartnerRelationshipEntity(UUID.randomUUID(), salesRep.partnerId, command.foreignId, command.start,
                                                                                                command.end, it))
            } else throw IllegalArgumentException("unexpected role $it")
        }

        log.info("persisting relationships")
        relationships.forEach { em.persist(it) }

        relationships.forEach { sendPartnerRelationshipChangedEvent(it) }
    }

    private fun sendPartnerRelationshipChangedEvent(relationship: PartnerRelationshipEntity) {
        partnerRelationshipEvent.fire(relationship)
    }

    @SuppressWarnings("unused")
    private fun send(@Observes(during = TransactionPhase.AFTER_SUCCESS) relationship: PartnerRelationshipEntity) {
        val prce = PartnerRelationshipChangedEvent(relationship.partnerId, relationship.foreignId, relationship.role)
        val msg = messageBuilder.build(relationship.foreignId, prce, event = "CHANGED_PARTNER_RELATIONSHIP")
        partnersOut.send(msg) // relationships also go on the same topic as actual partner changes
    }
}

data class PartnerRelationshipChangedEvent(
        val partnerId: UUID,
        val foreignId: String,
        val role: Role
)

class PartnerHasNoPrimaryAddressValidationException(msg: String): ValidationException(msg)