package ch.maxant.kdc.mf.contracts.control

import ch.maxant.kdc.mf.contracts.dto.Draft
import ch.maxant.kdc.mf.library.MessageBuilder
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.Observes
import javax.enterprise.event.TransactionPhase
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
    lateinit var messageBuilder: MessageBuilder

    @Inject
    private lateinit var somethingToSendEvent: javax.enterprise.event.Event<SomethingToSend>

    fun publish(draft: Draft) {

        send(eventBus, draft.contract.id, draft, event = "DRAFT_CREATED")
    }

    fun publish(createCaseCommand: CreateCaseCommand) {
        send(cases, createCaseCommand.referenceId, createCaseCommand, command = "CREATE_CASE")
    }

    private fun send(emitter: Emitter<String>, key: Any, value: Any, command: String? = null, event: String? = null) {
        somethingToSendEvent.fire(SomethingToSend(emitter, key, value, command, event))
    }

    // TODO use transactional outbox
    @SuppressWarnings("unused")
    private fun send(@Observes(during = TransactionPhase.AFTER_SUCCESS) sts: SomethingToSend) {
        sts.emitter.send(messageBuilder.build(sts.key, sts.value, sts.command, sts.event))
    }
}

// TODO move this to the lib, near the messageBuilder
private data class SomethingToSend(val emitter: Emitter<String>,
                                   val key: Any,
                                   val value: Any,
                                   val command: String?,
                                   val event: String?)

data class CreateCaseCommand(
        val referenceId: UUID,
        val caseType: String = "SALES"
)