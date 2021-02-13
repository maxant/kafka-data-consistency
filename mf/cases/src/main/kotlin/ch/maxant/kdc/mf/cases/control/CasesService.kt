package ch.maxant.kdc.mf.cases.control

import ch.maxant.kdc.mf.cases.boundary.CompleteTasksCommand
import ch.maxant.kdc.mf.cases.boundary.CreateCaseCommand
import ch.maxant.kdc.mf.cases.boundary.CreateTaskCommand
import ch.maxant.kdc.mf.cases.boundary.UpdateTaskCommand
import ch.maxant.kdc.mf.cases.entity.CaseEntity
import ch.maxant.kdc.mf.cases.entity.CaseType
import ch.maxant.kdc.mf.cases.entity.State
import ch.maxant.kdc.mf.cases.entity.TaskEntity
import ch.maxant.kdc.mf.library.AsyncContextAware
import ch.maxant.kdc.mf.library.JacksonConfig
import ch.maxant.kdc.mf.library.MessageBuilder
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Timed
import org.eclipse.microprofile.opentracing.Traced
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.jboss.logging.Logger
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.persistence.EntityManager
import kotlin.collections.HashMap

@ApplicationScoped
@SuppressWarnings("unused")
class CasesService(
        @Inject
        var em: EntityManager,

        @Inject
        var messageBuilder: MessageBuilder,

        @Inject
        var om: ObjectMapper
) {
    @Inject // this doesnt appear to work in the constructor
    @Channel("cases-out")
    lateinit var casesOut: Emitter<String>

    private val log = Logger.getLogger(this.javaClass)

    @AsyncContextAware
    @Timed(unit = MetricUnits.MILLISECONDS)
    @Traced
    fun createCase(caseCommand: CreateCaseCommand): CompletionStage<*> {
        log.info("creating a case: $caseCommand")

        val case = CaseEntity(UUID.randomUUID(), caseCommand.referenceId, caseCommand.caseType)

        em.persist(case)

        return sendCaseChangedEvent(case, emptyList())
    }

    @AsyncContextAware
    @Timed(unit = MetricUnits.MILLISECONDS)
    @Traced
    fun createTask(taskCommand: CreateTaskCommand): CompletionStage<*> {
        log.info("creating a task: $taskCommand")

        val case = CaseEntity.Queries.selectByReferenceId(em, taskCommand.referenceId)

        val task = TaskEntity(UUID.randomUUID(), case.id, taskCommand.userId,
                taskCommand.title, taskCommand.description, State.OPEN, taskCommand.action,
                om.writeValueAsString(taskCommand.params))

        em.persist(task)

        val tasks = TaskEntity.Queries.selectByCaseId(em, case.id)
        tasks.add(task)

        return sendCaseChangedEvent(case, tasks)
    }

    @AsyncContextAware
    @Timed(unit = MetricUnits.MILLISECONDS)
    @Traced
    fun updateTask(taskCommand: UpdateTaskCommand): CompletionStage<*> {
        log.info("updating a task: $taskCommand")

        val task = TaskEntity.Queries.selectByTaskId(em, taskCommand.taskId)
        task.description = taskCommand.description
        task.userId = taskCommand.userId
        task.title = taskCommand.title
        task.state = taskCommand.state

        val case = CaseEntity.Queries.selectByCaseId(em, task.caseId)

        val tasks = TaskEntity.Queries.selectByCaseId(em, case.id)

        return sendCaseChangedEvent(case, tasks)
    }

    @AsyncContextAware
    @Timed(unit = MetricUnits.MILLISECONDS)
    @Traced
    fun completeTasks(tasksCommand: CompleteTasksCommand): CompletionStage<*> {
        log.info("completing tasks: $tasksCommand")

        val case = CaseEntity.Queries.selectByReferenceId(em, tasksCommand.referenceId)

        val allTasks = TaskEntity.Queries.selectByCaseId(em, case.id)
        allTasks.filter { tasksCommand.action == it.action }.forEach { it.state = State.DONE }

        return sendCaseChangedEvent(case, allTasks)
    }

    private fun sendCaseChangedEvent(case: CaseEntity, tasks: List<TaskEntity>): CompletionStage<*> {
        val cce = CaseChangedEvent(case, tasks)
        val ack = CompletableFuture<Unit>()
        val msg = messageBuilder.build(case.referenceId, cce, ack, event = "CHANGED_CASE")
        casesOut.send(msg)
        return ack
    }
}

data class CaseChangedEvent(
        val caseId: UUID,
        val referenceId: UUID,
        val type: CaseType,
        val tasks: List<TaskDto>
) {
    constructor(case: CaseEntity, tasks: List<TaskEntity>) : this(case.id, case.referenceId, case.type, tasks.map { TaskDto(it) })
}

data class TaskDto(
        val taskId: UUID,
        val userId: String,
        val title: String,
        val description: String,
        val state: State,
        val action: String?,
        val params: Map<String, String>?
) {
    constructor(task: TaskEntity) : this(task.id, task.userId, task.title, task.description, task.state, task.action, toMap(task.params))
}

private fun toMap(s: String?): Map<String, String> {
    return JacksonConfig.om.readValue<HashMap<String, String>>(s?:"{}")
}
