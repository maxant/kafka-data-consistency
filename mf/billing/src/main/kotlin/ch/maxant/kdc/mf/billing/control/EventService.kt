package ch.maxant.kdc.mf.billing.control

import ch.maxant.kdc.mf.billing.boundary.Contract
import ch.maxant.kdc.mf.billing.control.BillingService
import ch.maxant.kdc.mf.library.Context
import ch.maxant.kdc.mf.library.KafkaHandler
import ch.maxant.kdc.mf.library.MessageBuilder
import ch.maxant.kdc.mf.library.PimpedAndWithDltAndAck
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.smallrye.reactive.messaging.kafka.OutgoingKafkaRecordMetadata
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.reactive.messaging.Message
import org.jboss.logging.Logger
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject

@ApplicationScoped
@SuppressWarnings("unused")
class EventService(
        @Inject
        var om: ObjectMapper
) {
    @Inject
    @Channel("events-out")
    lateinit var events: Emitter<String>

    fun sendEvent(event: Event) {
        val md = OutgoingKafkaRecordMetadata.builder<String>()
                .withKey(event.jobId.toString())
                .build()
        val msg = Message.of(om.writeValueAsString(event))
        events.send(msg.addMetadata(md))
    }
}

data class Event(val action: Action, val jobId: UUID, val groupId: UUID, val contractId: UUID?, val contract: Contract?, val completed: LocalDateTime? = null) {
    constructor(action: Action, jobId: UUID, groupId: UUID): this(action, jobId, groupId, null, null)
}

enum class Action {
    SENT_TO_PRICING, FAILED_IN_PRICING,
    SENT_TO_BILLING, FAILED_IN_BILLING,
    COMPLETED,
    COMPLETED_GROUP
}


