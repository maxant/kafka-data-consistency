package ch.maxant.kdc.mf.contracts.control

import ch.maxant.kdc.mf.contracts.dto.*
import ch.maxant.kdc.mf.library.MessageBuilder
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.jboss.logging.Logger
import java.util.concurrent.CompletableFuture
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
    @Channel("partners-out")
    lateinit var partners: Emitter<String>

    @Inject
    lateinit var messageBuilder: MessageBuilder

    @Inject
    private lateinit var somethingToSendEvent: javax.enterprise.event.Event<SomethingToSend>

    val log: Logger = Logger.getLogger(this.javaClass)

    fun publish(draft: Draft) {
        send(eventBus, draft.contract.id, draft, event = "CREATED_DRAFT")
    }

    fun publish(updatedDraft: UpdatedDraft) {
        send(eventBus, updatedDraft.contract.id, updatedDraft, event = "UPDATED_DRAFT")
    }

    fun publish(offeredDraft: OfferedDraft) {
        send(eventBus, offeredDraft.contract.id, offeredDraft, event = "OFFERED_DRAFT")
    }

    fun publish(createCaseCommand: CreateCaseCommand) {
        send(cases, createCaseCommand.referenceId, createCaseCommand, command = "CREATE_CASE")
    }

    fun publish(createTaskCommand: CreateTaskCommand) {
        send(cases, createTaskCommand.referenceId, createTaskCommand, command = "CREATE_TASK")
    }

    fun publish(completeTasksCommand: CompleteTasksCommand) {
        send(cases, completeTasksCommand.referenceId, completeTasksCommand, command = "COMPLETE_TASKS")
    }

    fun publish(createPartnerRelationshipCommand: CreatePartnerRelationshipCommand) {
        send(partners, createPartnerRelationshipCommand.foreignId, createPartnerRelationshipCommand, command = "CREATE_PARTNER_RELATIONSHIP")
    }

    private fun send(emitter: Emitter<String>, key: Any, value: Any, command: String? = null, event: String? = null) {
        somethingToSendEvent.fire(SomethingToSend(emitter, key, value, command, event))
    }

    // TODO use transactional outbox
    @SuppressWarnings("unused")
    private fun send(@Observes(during = TransactionPhase.AFTER_SUCCESS) sts: SomethingToSend) {
        // since this is happening async after the transaction, and we don't return anything,
        // we just pass a new CompletableFuture and don't care what happens with it
        sts.emitter.send(messageBuilder.build(sts.key, sts.value, CompletableFuture(), sts.command, sts.event))
        log.info("published ${sts.command?:sts.event}")
    }
}

// TODO move this to the lib, near the messageBuilder
private data class SomethingToSend(val emitter: Emitter<String>,
                                   val key: Any,
                                   val value: Any,
                                   val command: String?,
                                   val event: String?)

