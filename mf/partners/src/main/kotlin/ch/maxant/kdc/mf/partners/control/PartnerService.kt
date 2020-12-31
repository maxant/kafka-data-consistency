package ch.maxant.kdc.mf.partners.control

import ch.maxant.kdc.mf.library.AsyncContextAware
import ch.maxant.kdc.mf.library.MessageBuilder
import ch.maxant.kdc.mf.partners.adapter.OrganisationAdapter
import ch.maxant.kdc.mf.partners.boundary.CreatePartnerRelationshipCommand
import ch.maxant.kdc.mf.partners.entity.*
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.jboss.logging.Logger
import java.lang.IllegalArgumentException
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.persistence.EntityManager
import javax.validation.ValidationException

@ApplicationScoped
@SuppressWarnings("unused")
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

    private val log = Logger.getLogger(this.javaClass)

    @AsyncContextAware
    fun createRelationship(command: CreatePartnerRelationshipCommand): CompletionStage<*> {
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

        return CompletableFuture.allOf(*relationships
                .map { sendPartnerRelationshipChangedEvent(it) }
                .map { it.toCompletableFuture() }
                .toTypedArray())
    }

    private fun sendPartnerRelationshipChangedEvent(relationship: PartnerRelationshipEntity): CompletionStage<*> {
        val prce = PartnerRelationshipChangedEvent(relationship.partnerId, relationship.foreignId, relationship.role)
        val ack = CompletableFuture<Unit>()
        val msg = messageBuilder.build(relationship.foreignId, prce, ack, event = "CHANGED_PARTNER_RELATIONSHIP")
        partnersOut.send(msg) // relationships also go on the same topic as actual partner changes
        return ack
    }

}

data class PartnerRelationshipChangedEvent(
        val partnerId: UUID,
        val foreignId: String,
        val role: Role
)

class PartnerHasNoPrimaryAddressValidationException(msg: String): ValidationException(msg)