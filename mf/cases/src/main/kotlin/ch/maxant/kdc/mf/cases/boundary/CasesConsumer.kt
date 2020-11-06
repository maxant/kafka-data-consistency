package ch.maxant.kdc.mf.cases.boundary

import ch.maxant.kdc.mf.cases.entity.CaseEntity
import ch.maxant.kdc.mf.cases.entity.CaseType
import ch.maxant.kdc.mf.cases.entity.State
import ch.maxant.kdc.mf.cases.entity.TaskEntity
import ch.maxant.kdc.mf.library.Context
import ch.maxant.kdc.mf.library.MessageBuilder
import ch.maxant.kdc.mf.library.PimpedAndWithDltAndAck
import com.fasterxml.jackson.databind.ObjectMapper
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
        var om: ObjectMapper,

        @Inject
        var messageBuilder: MessageBuilder,

        @Inject
        var context: Context
) {
    @Inject // this doesnt appear to work in the constructor
    @Channel("cases-out")
    lateinit var eventBus: Emitter<String>

    private val log = Logger.getLogger(this.javaClass)

    @Incoming("cases-in")
    @Transactional
    @PimpedAndWithDltAndAck
    @SuppressWarnings("unused")
    fun process(msg: Message<String>) {
        val command = Command.valueOf(context.command!!)
        when {
            Command.CREATE_CASE == command -> { createCase(msg.payload) }
            Command.CREATE_TASK == command -> { createTask(msg.payload) }
            Command.UPDATE_TASK == command -> { updateTask(msg.payload) }
            else -> { throw RuntimeException("unexpected command $command: $msg") }
        }
    }

    private fun createCase(msg: String) {
        val caseRequest = om.readValue(msg, CreateCaseCommand::class.java)
        log.info("creating a case: $caseRequest")

        val case = CaseEntity(UUID.randomUUID(), caseRequest.referenceId, caseRequest.caseType)

        em.persist(case)

        sendCaseChangedEvent(case, emptyList())
    }

    private fun createTask(msg: String) {
        val taskRequest = om.readValue(msg, CreateTaskCommand::class.java)
        log.info("creating a task: $taskRequest")

        val case = CaseEntity.Queries.selectByReferenceId(em, taskRequest.referenceId)

        val task = TaskEntity(UUID.randomUUID(), case.id, taskRequest.userId,
                taskRequest.title, taskRequest.description, State.OPEN)

        em.persist(task)

        val tasks = TaskEntity.Queries.selectByCaseId(em, case.id)
        tasks.add(task)

        sendCaseChangedEvent(case, tasks)
    }

    private fun updateTask(msg: String) {
        val taskRequest = om.readValue(msg, UpdateTaskCommand::class.java)
        log.info("updating a task: $taskRequest")

        val task = TaskEntity.Queries.selectByTaskId(em, taskRequest.taskId)
        task.description = taskRequest.description
        task.userId = taskRequest.userId
        task.title = taskRequest.title
        task.state = taskRequest.state

        val case = CaseEntity.Queries.selectByCaseId(em, task.caseId)

        val tasks = TaskEntity.Queries.selectByCaseId(em, case.id)

        sendCaseChangedEvent(case, tasks)
    }

    private fun sendCaseChangedEvent(case: CaseEntity, tasks: List<TaskEntity>) {
        val cce = CaseChangedEvent(case.id, case.referenceId, case.type, tasks.map { TaskDto(it) })
        val msg = messageBuilder.build(case.referenceId, cce, event = "CASE_CHANGED")
        eventBus.send(msg)
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
        val referenceId: UUID, // used to locate the case
        val userId: String,
        val title: String,
        val description: String
)

data class UpdateTaskCommand(
        val taskId: UUID, // used to locate the task, and load the case
        val userId: String,
        val title: String,
        val description: String,
        val state: State
)

data class CaseChangedEvent(
        val caseId: UUID,
        val referenceId: UUID,
        val type: CaseType,
        val tasks: List<TaskDto>
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