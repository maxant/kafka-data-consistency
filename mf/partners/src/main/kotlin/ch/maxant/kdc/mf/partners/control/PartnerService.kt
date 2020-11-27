package ch.maxant.kdc.mf.partners.control

import ch.maxant.kdc.mf.library.AsyncContextAware
import ch.maxant.kdc.mf.library.MessageBuilder
import ch.maxant.kdc.mf.partners.boundary.CreatePartnerRelationshipCommand
import ch.maxant.kdc.mf.partners.entity.PartnerRelationshipEntity
import ch.maxant.kdc.mf.partners.entity.Role
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.jboss.logging.Logger
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.persistence.EntityManager

@ApplicationScoped
@SuppressWarnings("unused")
class PartnerService(
        @Inject
        var em: EntityManager,

        @Inject
        var messageBuilder: MessageBuilder
) {
    @Inject // this doesnt appear to work in the constructor
    @Channel("partners-out")
    lateinit var partnersOut: Emitter<String>

    private val log = Logger.getLogger(this.javaClass)

    @AsyncContextAware
    fun createRelationship(command: CreatePartnerRelationshipCommand): CompletionStage<*> {
        log.info("creating a partner relationship: $command")

        val relationship = PartnerRelationshipEntity(UUID.randomUUID(), command.partnerId, command.foreignId, command.start, command.end, Role.valueOf(command.role))
        em.persist(relationship)

        return sendPartnerRelationshipChangedEvent(relationship)
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
