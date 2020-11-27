package ch.maxant.kdc.mf.partners.boundary

import ch.maxant.kdc.mf.library.Context
import ch.maxant.kdc.mf.library.PimpedAndWithDltAndAck
import ch.maxant.kdc.mf.partners.control.PartnerService
import ch.maxant.kdc.mf.partners.entity.Role
import com.fasterxml.jackson.databind.ObjectMapper
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.eclipse.microprofile.reactive.messaging.Message
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.CompletionStage
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.transaction.Transactional

@ApplicationScoped
@SuppressWarnings("unused")
class CasesConsumer(
        @Inject
        var om: ObjectMapper,

        @Inject
        var context: Context,

        @Inject
        var partnerService: PartnerService
) {
    @Incoming("partners-in")
    @Transactional
    @PimpedAndWithDltAndAck
    @SuppressWarnings("unused")
    fun process(msg: Message<String>): CompletionStage<*> {
        val command = Command.valueOf(context.command!!)
        return when {
            Command.CREATE_PARTNER_RELATIONSHIP == command ->
                partnerService.createRelationship(om.readValue(msg.payload, CreatePartnerRelationshipCommand::class.java))
            else -> throw RuntimeException("unexpected command $command: $msg")
       }
    }
}

enum class Command {
    CREATE_PARTNER_RELATIONSHIP
}

data class CreatePartnerRelationshipCommand(
        val partnerId: UUID,
        val foreignId: String,
        val role: String,
        val start: LocalDateTime,
        val end: LocalDateTime
)
