package ch.maxant.kdc.mf.cases.boundary

import ch.maxant.kdc.mf.cases.entity.CaseEntity
import ch.maxant.kdc.mf.cases.entity.CaseType
import ch.maxant.kdc.mf.cases.entity.State
import ch.maxant.kdc.mf.cases.entity.TaskEntity
import ch.maxant.kdc.mf.library.ErrorsHandled
import com.fasterxml.jackson.databind.ObjectMapper
import io.smallrye.reactive.messaging.annotations.Blocking
import io.smallrye.reactive.messaging.kafka.OutgoingKafkaRecordMetadata
import org.apache.kafka.common.header.internals.RecordHeader
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.eclipse.microprofile.reactive.messaging.Message
import org.jboss.logging.Logger
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.persistence.EntityManager
import javax.transaction.Transactional

@ApplicationScoped
@SuppressWarnings("unused")
class CasesConsumer(
        @Inject
        var em: EntityManager,

        @Inject
        var om: ObjectMapper
) {
    @Inject // this doesnt appear to work in the constructor
    @Channel("cases-out")
    lateinit var eventBus: Emitter<String>

    private val log = Logger.getLogger(this.javaClass)

    @Incoming("cases-in")
    @Blocking
    @Transactional
    @ErrorsHandled
    @SuppressWarnings("unused")
    fun process(msg: String) {
        val event = om.readTree(msg)
        val command = Command.valueOf(event.get("command").textValue())
        when {
            Command.CREATE_CASE == command -> { createCase(msg, command) }
            Command.CREATE_TASK == command -> { createTask(msg, command) }
            Command.UPDATE_TASK == command -> { updateTask(msg, command) }
            else -> { throw RuntimeException("unexpected command $command: $msg") }
        }
    }

    private fun createCase(msg: String, command: Command) {
        val caseRequest = om.readValue(msg, CreateCaseCommand::class.java)
        log.info("creating a case: $caseRequest")

        val case = CaseEntity(UUID.randomUUID(), caseRequest.referenceId, caseRequest.caseType)

        em.persist(case)

        sendCaseChangedEvent(caseRequest.requestId, case, command, emptyList())
    }

    private fun createTask(msg: String, command: Command) {
        val taskRequest = om.readValue(msg, CreateTaskCommand::class.java)
        log.info("creating a task: $taskRequest")

        val case = CaseEntity.Queries.selectByReferenceId(em, taskRequest.referenceId)

        val task = TaskEntity(UUID.randomUUID(), case.id, taskRequest.userId,
                taskRequest.title, taskRequest.description, State.OPEN)

        em.persist(task)

        val tasks = TaskEntity.Queries.selectByCaseId(em, case.id)
        tasks.add(task)

        sendCaseChangedEvent(taskRequest.requestId, case, command, tasks)
    }

    private fun updateTask(msg: String, command: Command) {
        val taskRequest = om.readValue(msg, UpdateTaskCommand::class.java)
        log.info("updating a task: $taskRequest")

        val task = TaskEntity.Queries.selectByTaskId(em, taskRequest.taskId)
        task.description = taskRequest.description
        task.userId = taskRequest.userId
        task.title = taskRequest.title
        task.state = taskRequest.state

        val case = CaseEntity.Queries.selectByCaseId(em, task.caseId)

        val tasks = TaskEntity.Queries.selectByCaseId(em, case.id)

        sendCaseChangedEvent(taskRequest.requestId, case, command, tasks)
    }

    private fun sendCaseChangedEvent(requestId: UUID, case: CaseEntity, command: Command, tasks: List<TaskEntity>) {
        val metadata = OutgoingKafkaRecordMetadata.builder<Any>()
                .withKey(case.referenceId.toString())
                .withHeaders(listOf(RecordHeader("requestId", requestId.toString().toByteArray())))
                .build()

        val cce = CaseChangedEvent(requestId, case.id, case.referenceId, case.type, command, tasks.map { TaskDto(it) })

        eventBus.send(Message.of(om.writeValueAsString(cce)).addMetadata(metadata))
    }
}

enum class Command {
    CREATE_CASE,
    CREATE_TASK,
    UPDATE_TASK
}

data class CreateCaseCommand(
        val requestId: UUID,
        val referenceId: UUID,
        val caseType: CaseType
)

/** one of caseId or referenceId is required. all others are required */
data class CreateTaskCommand(
        val requestId: UUID,
        val referenceId: UUID, // used to locate the case
        val userId: String,
        val title: String,
        val description: String
)

data class UpdateTaskCommand(
        val requestId: UUID,
        val taskId: UUID, // used to locate the task, and load the case
        val userId: String,
        val title: String,
        val description: String,
        val state: State
)

data class CaseChangedEvent(
        val requestId: UUID,
        val caseId: UUID,
        val referenceId: UUID,
        val type: CaseType,
        val originalCommand: Command,
        val tasks: List<TaskDto>,
        val event: String = "CASE_CHANGED"
)

data class TaskDto(
        val taskId: UUID,
        val userId: String,
        val title: String,
        val description: String,
        val state: State
) {
    constructor(task: TaskEntity) : this(task.id, task.userId, task.title, task.description, task.state)
}