package ch.maxant.kdc.mf.contracts.control

import ch.maxant.kdc.mf.contracts.dto.Draft
import com.fasterxml.jackson.databind.ObjectMapper
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject

@ApplicationScoped
class EventBus {

    // TODO how come constructor injection doesnt work with emitter? related to kotlin?
    @Inject
    @Channel("event-bus-out")
    lateinit var eventBus: Emitter<String>

    @Inject
    @Channel("cases-out")
    lateinit var cases: Emitter<String>

    @Inject
    lateinit var om: ObjectMapper

    fun publish(event: Event<*>) {
        // TODO set key as contractId
        // TODO transactional outbox
        eventBus.send(om.writeValueAsString(event))
    }

    fun publish(createCaseCommand: CreateCaseCommand) {
        // TODO set key as contractId
        // TODO transactional outbox
        cases.send(om.writeValueAsString(createCaseCommand))
    }
}

enum class Events {
    DRAFT_CREATED
}

abstract class Event<T>(open val requestId: UUID, val event: Events, open val value: T)

data class DraftEvent(override val requestId: UUID, override val value: Draft) :
        Event<Draft>(requestId, Events.DRAFT_CREATED, value)

data class CreateCaseCommand (
        val requestId: UUID,
        val referenceId: UUID,
        val command: String = "CREATE_CASE",
        val caseType: String = "SALES"
)