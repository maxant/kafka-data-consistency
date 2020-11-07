package ch.maxant.kdc.mf.cases.boundary

import ch.maxant.kdc.mf.cases.control.CasesService
import ch.maxant.kdc.mf.cases.entity.CaseType
import ch.maxant.kdc.mf.cases.entity.State
import ch.maxant.kdc.mf.cases.entity.TaskEntity
import ch.maxant.kdc.mf.library.Context
import ch.maxant.kdc.mf.library.PimpedAndWithDltAndAck
import com.fasterxml.jackson.databind.ObjectMapper
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.eclipse.microprofile.reactive.messaging.Message
import java.util.*
import java.util.concurrent.CompletionStage
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.transaction.Transactional

@ApplicationScoped
@SuppressWarnings("unused")
class CasesConsumer(
        @Inject
        var om: ObjectMapper,

        @Inject
        var context: Context,

        @Inject
        var casesService: CasesService
) {
    @Incoming("cases-in")
    @Transactional
    @PimpedAndWithDltAndAck
    @SuppressWarnings("unused")
    fun process(msg: Message<String>): CompletionStage<*> {
        val command = Command.valueOf(context.command!!)
        return when {
            Command.CREATE_CASE == command ->
                casesService.createCase(om.readValue(msg.payload, CreateCaseCommand::class.java))
            Command.CREATE_TASK == command ->
                casesService.createTask(om.readValue(msg.payload, CreateTaskCommand::class.java))
            Command.UPDATE_TASK == command ->
                casesService.updateTask(om.readValue(msg.payload, UpdateTaskCommand::class.java))
            else -> throw RuntimeException("unexpected command $command: $msg")
        }
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