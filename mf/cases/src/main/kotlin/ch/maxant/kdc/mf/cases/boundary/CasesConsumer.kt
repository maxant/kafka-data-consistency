package ch.maxant.kdc.mf.cases.boundary

import ch.maxant.kdc.mf.cases.entity.CaseEntity
import ch.maxant.kdc.mf.cases.entity.CaseType
import ch.maxant.kdc.mf.cases.entity.Status
import ch.maxant.kdc.mf.cases.entity.TaskEntity
import ch.maxant.kdc.mf.library.ErrorHandler
import com.fasterxml.jackson.databind.ObjectMapper
import io.smallrye.reactive.messaging.annotations.Blocking
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.jboss.logging.Logger
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.persistence.EntityManager
import javax.transaction.Transactional

@ApplicationScoped
class CasesConsumer(
        @Inject
        var em: EntityManager,

        @Inject
        var errorHandler: ErrorHandler,

        @Inject
        var om: ObjectMapper
) {
    @Inject // this doesnt appear to work in the constructor
    @Channel("cases-out")
    lateinit var eventBus: Emitter<String>

    val log = Logger.getLogger(this.javaClass)

    @Incoming("cases-in")
    @Blocking
    @Transactional
    fun process(msg: String) {
        val event = om.readTree(msg)
        try {
            val command = Command.valueOf(event.get("command").textValue())
            when {
                Command.CREATE_CASE == command -> { createCase(msg, command) }
                Command.CREATE_TASK == command -> { createTask(msg, command) }
                Command.UPDATE_TASK == command -> { updateTask(msg, command) }
                else -> { throw RuntimeException("unexpected command $command: $msg") }
            }
        } catch (e: Exception) {
            log.error("failed to process message $msg", e) // TODO replace with DLT and suitable error processing
            errorHandler.dlt(event.get("referenceId")?.asText(), e, msg)
        }
    }

    private fun createCase(msg: String, command: Command) {
        val caseRequest = om.readValue(msg, CreateCaseCommand::class.java)
        log.info("creating a case: $caseRequest")

        val case = CaseEntity(UUID.randomUUID(), caseRequest.referenceId, caseRequest.caseType)

        em.persist(case)

        eventBus.send(om.writeValueAsString(
                CaseChangedEvent(case.id, case.referenceId, case.type, command, emptyList())))
    }

    private fun createTask(msg: String, command: Command) {
        val taskRequest = om.readValue(msg, CreateTaskCommand::class.java)
        log.info("creating a task: $taskRequest")

        val case = CaseEntity.Queries.selectByReferenceId(em, taskRequest.referenceId)

        val task = TaskEntity(UUID.randomUUID(), taskRequest.caseId, taskRequest.userId,
                taskRequest.title, taskRequest.description, Status.OPEN)

        em.persist(task)

        val tasks = TaskEntity.Queries.selectByCaseId(em, case.id)
        tasks.add(task)

        eventBus.send(om.writeValueAsString(
                CaseChangedEvent(case.id, case.referenceId, case.type, command, tasks.map { TaskDto(it) })))
    }

    private fun updateTask(msg: String, command: Command) {
        val taskRequest = om.readValue(msg, UpdateTaskCommand::class.java)
        log.info("updating a task: $taskRequest")

        val task = TaskEntity.Queries.selectByTaskId(em, taskRequest.taskId)
        task.description = taskRequest.description
        task.userId = taskRequest.userId
        task.title = taskRequest.title
        task.status = taskRequest.status

        val case = CaseEntity.Queries.selectByCaseId(em, task.caseId)

        val tasks = TaskEntity.Queries.selectByCaseId(em, case.id)

        eventBus.send(om.writeValueAsString(
                CaseChangedEvent(case.id, case.referenceId, case.type, command, tasks.map { TaskDto(it) })))
    }
}

enum class Command {
    CREATE_CASE,
    CREATE_TASK,
    UPDATE_TASK
}

data class CreateCaseCommand(
        val referenceId: UUID,
        val caseType: CaseType
)

/** one of caseId or referenceId is required. all others are required */
data class CreateTaskCommand(
        var caseId: UUID,
        val referenceId: UUID,
        val userId: String,
        val title: String,
        val description: String
)

data class UpdateTaskCommand(
        val referenceId: UUID,
        val taskId: UUID,
        val userId: String,
        val title: String,
        val description: String,
        val status: Status
)

data class CaseChangedEvent(
        val caseId: UUID,
        val referenceId: UUID,
        val type: CaseType,
        val command: Command,
        val tasks: List<TaskDto>,
        val event: String = "CASE_CHANGED"
)

data class TaskDto(
        val taskId: UUID,
        val userId: String,
        val title: String,
        val description: String,
        val status: Status
) {
    constructor(task: TaskEntity) : this(task.id, task.userId, task.title, task.description, task.status)
}