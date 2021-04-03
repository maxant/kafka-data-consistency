package ch.maxant.kdc.mf.cases.boundary

import ch.maxant.kdc.mf.cases.control.CasesService
import ch.maxant.kdc.mf.cases.entity.CaseType
import ch.maxant.kdc.mf.cases.entity.State
import ch.maxant.kdc.mf.library.Context
import ch.maxant.kdc.mf.library.KafkaHandler
import ch.maxant.kdc.mf.library.PimpedAndWithDltAndAck
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Timed
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.transaction.Transactional


@ApplicationScoped
@SuppressWarnings("unused")
class CasesConsumer(
        @Inject val om: ObjectMapper,
        @Inject val context: Context,
        @Inject val casesService: CasesService
) : KafkaHandler {

    override fun getKey() = "cases-in"

    override fun getRunInParallel() = false

    @PimpedAndWithDltAndAck
    @Timed(unit = MetricUnits.MILLISECONDS)
    override fun handle(record: ConsumerRecord<String, String>) {
        val command = Command.valueOf(context.command!!)
        when {
            Command.CREATE_CASE == command ->
                casesService.createCase(om.readValue(record.value(), CreateCaseCommand::class.java))
            Command.CREATE_TASK == command ->
                casesService.createTask(om.readValue(record.value(), CreateTaskCommand::class.java))
            Command.UPDATE_TASK == command ->
                casesService.updateTask(om.readValue(record.value(), UpdateTaskCommand::class.java))
            Command.COMPLETE_TASKS == command ->
                casesService.completeTasks(om.readValue(record.value(), CompleteTasksCommand::class.java))
            else -> throw RuntimeException("unexpected command $command: ${record.value()}")
        }
    }
}

enum class Command {
    CREATE_CASE,
    CREATE_TASK,
    UPDATE_TASK,
    COMPLETE_TASKS
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
        val description: String,
        val action: String?,
        val params: Map<String, String>
)

data class UpdateTaskCommand(
        val taskId: UUID, // used to locate the task, and load the case
        val userId: String,
        val title: String,
        val description: String,
        val state: State
)

data class CompleteTasksCommand(
        val referenceId: UUID,
        val action: String
)

