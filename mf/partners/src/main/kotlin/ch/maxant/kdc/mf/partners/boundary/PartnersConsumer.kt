package ch.maxant.kdc.mf.partners.boundary

import ch.maxant.kdc.mf.library.Context
import ch.maxant.kdc.mf.library.KafkaHandler
import ch.maxant.kdc.mf.library.PimpedAndWithDltAndAck
import ch.maxant.kdc.mf.partners.control.PartnerService
import ch.maxant.kdc.mf.partners.entity.Role
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Timed
import java.time.LocalDateTime
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject

@ApplicationScoped
@SuppressWarnings("unused")
class PartnersConsumer(
        @Inject
        var om: ObjectMapper,

        @Inject
        var context: Context,

        @Inject
        var partnerService: PartnerService

) : KafkaHandler {

    override fun getKey() = "partners-in"

    override fun getRunInParallel() = true

    @PimpedAndWithDltAndAck
    @SuppressWarnings("unused")
    @Timed(unit = MetricUnits.MILLISECONDS)
    override fun handle(record: ConsumerRecord<String, String>) {
        when (Command.valueOf(context.command!!)) {
            Command.CREATE_PARTNER_RELATIONSHIP ->
                partnerService.createRelationship(om.readValue(record.value(), CreatePartnerRelationshipCommand::class.java))
        }
    }
}

enum class Command {
    CREATE_PARTNER_RELATIONSHIP
}

data class CreatePartnerRelationshipCommand(
        val partnerId: UUID,
        val foreignId: String,
        val role: Role,
        val start: LocalDateTime,
        val end: LocalDateTime,
        val additionalRelationshipsToCreate: List<Role>
)
