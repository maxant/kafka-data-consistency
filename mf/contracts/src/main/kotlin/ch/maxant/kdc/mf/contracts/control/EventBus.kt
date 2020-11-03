package ch.maxant.kdc.mf.contracts.control

import ch.maxant.kdc.mf.contracts.dto.Draft
import com.fasterxml.jackson.databind.ObjectMapper
import io.smallrye.reactive.messaging.kafka.OutgoingKafkaRecordMetadata
import org.apache.kafka.common.header.internals.RecordHeader
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.reactive.messaging.Message
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject


@ApplicationScoped
class EventBus {

    // FIXME how come constructor injection doesnt work with emitter? related to kotlin?
    @Inject
    @Channel("event-bus-out")
    lateinit var eventBus: Emitter<String>

    @Inject
    @Channel("cases-out")
    lateinit var cases: Emitter<String>

    @Inject
    lateinit var om: ObjectMapper

    fun publish(event: Event<*>) {
        val referenceId = when(event) {
            is DraftEvent -> event.value.contract.id
            else -> throw TODO("unexpected event type ${event.javaClass}")
        }
        send(eventBus, event.requestId, referenceId, event)
    }

    fun publish(createCaseCommand: CreateCaseCommand) {
        send(cases, createCaseCommand.requestId, createCaseCommand.referenceId, createCaseCommand)
    }

    private fun send(emitter: Emitter<String>, requestId: UUID, referenceId: UUID, value: Any) {
        // TODO transactional outbox
        val metadata = OutgoingKafkaRecordMetadata.builder<Any>()
                .withKey(referenceId.toString()) // normally contractId
                .withHeaders(listOf(RecordHeader("requestId", requestId.toString().toByteArray())))
                .build()
        emitter.send(Message.of(om.writeValueAsString(value)).addMetadata(metadata))
    }
}

enum class Events {
    DRAFT_CREATED
}

abstract class Event<T>(open val requestId: UUID, val event: Events, open val value: T)

data class DraftEvent(override val requestId: UUID, override val value: Draft) :
        Event<Draft>(requestId, Events.DRAFT_CREATED, value)

data class CreateCaseCommand(
        val requestId: UUID,
        val referenceId: UUID,
        val command: String = "CREATE_CASE",
        val caseType: String = "SALES"
)