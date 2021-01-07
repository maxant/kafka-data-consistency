package ch.maxant.kdc.mf.billing.boundary

import ch.maxant.kdc.mf.billing.dto.ContractDto
import ch.maxant.kdc.mf.billing.dto.GroupDto
import ch.maxant.kdc.mf.library.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.smallrye.reactive.messaging.kafka.OutgoingKafkaRecordMetadata
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.reactive.messaging.Message
import org.jboss.logging.Logger
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject

@ApplicationScoped
@SuppressWarnings("unused")
class BillingConsumer(
        @Inject
        var om: ObjectMapper,

        @Inject
        var context: Context,

        @ConfigProperty(name = "ch.maxant.kdc.mf.billing.group.size", defaultValue = "50")
        var groupSize: Int,

        @Inject
        var messageBuilder: MessageBuilder,

        @Inject
        var billingStreamApplication: BillingStreamApplication

) : KafkaHandler {

    @Inject
    @Channel("contracts-event-bus-out")
    lateinit var contractEventBus: Emitter<String>

    @Inject
    @Channel("jobs-out")
    lateinit var jobs: Emitter<String>

    @Inject
    @Channel("groups-out")
    lateinit var groups: Emitter<String>

    @Inject
    @Channel("contracts-out")
    lateinit var contracts: Emitter<String>

    private val log = Logger.getLogger(this.javaClass)

    override fun getKey() = "billing-in"

    override fun getRunInParallel() = true

    /**
     * this is the general entry point into the billing application. this is either called because a contract is
     * approved, or because the scheduled recurring billing process has found contracts that are not yet billed.
     * not using kafka streams api here, because we need easy access to headers.
     */
    @PimpedAndWithDltAndAck
    override fun handle(record: ConsumerRecord<String, String>) {
        when (context.command) {
            BILL_GROUP -> trackStateAndSendForPricing(record.value())
            else -> TODO("unknown message ${context.event}")
        }
    }

    fun trackStateAndSendForPricing(value: String) {
        val group = om.readValue<Group>(value)
        log.info("billing group ${group.id} from job ${group.jobId}")

        sendNewGroup(group)

        val contractsInGroup = mutableListOf<ContractDto>()
        for((i, contract) in group.contracts.withIndex()) {
            contractsInGroup.add(contract)

            if(i % groupSize == 0) {
                val allCommands = mutableListOf<PricingCommand>()
                contractsInGroup.forEach {
                    val commands = it.basePeriodsToPrice.map { PricingCommand(contract.id, it.from, it.to) }

                    publishInternalState("contract::${contract.id}",  ContractState(selection.id, contractId, ContractState.State.SENT_FOR_PRICING.toString())
                }
                send(PricingCommandGroup(selection.id, commands))

                commands.map { it.contractId }
                        .distinct()
                        .forEach { contractId ->
                        }

                contractsInGroup.clear()
            }
        }

        // send last group if necessary
        if(commands.isNotEmpty()) {
            send(PricingCommandGroup(selection.id, commands))
        }

        saveGroupState(group.id, SelectionState(selection.id, SelectionState.State.STARTED, SelectionState.Counts())
    }

    private fun sendNewGroup(group: Group) {
        val md = OutgoingKafkaRecordMetadata.builder<String>()
                        .withKey(group.jobId.toString())
                        .build()
        val msg = Message.of(om.writeValueAsString(JobEvent(JobAction.NEW_GROUP, group.jobId, group.id)))
        jobs.send(msg.addMetadata(md))
    }

    private fun publishInternalState(key: String, value: String) {
        val metadata = OutgoingKafkaRecordMetadata.builder<String>()
                .withKey(key)
                .build()
        internalState.send(Message.of(value).addMetadata(metadata))
    }

    private fun saveGroupState(selectionId: UUID, value: String) {
        val metadata = OutgoingKafkaRecordMetadata.builder<String>()
                .withKey(selectionId.toString())
                .build()
        internalSelections.send(Message.of(value).addMetadata(metadata))
    }

    private fun send(pricingCommandGroup: PricingCommandGroup) {
        // TODO transactional outbox
        contractEventBus.send(messageBuilder.build(pricingCommandGroup.groupId, pricingCommandGroup, command = "CALCULATE_PRICES"))
        log.info("published pricing command groupId ${pricingCommandGroup.groupId} with contractIds ${pricingCommandGroup.contents.map {it.contractId}}")
    }

    companion object {
        const val BILL_GROUP = "BILL_GROUP"
    }
}

data class PricingCommandGroup(val groupId: UUID, val commands: List<PricingCommand>)

data class PricingCommand(val contractId: UUID, val from: LocalDate, val to: LocalDate)

data class JobEvent(val action: JobAction, val jobId: UUID, val groupId: UUID, val completed: LocalDateTime? = null)

enum class JobAction {
    NEW_GROUP, GROUP_SENT_TO_PRICING, GROUP_FAILED_IN_PRICING,
    GROUP_SENT_TO_BILLING, GROUP_FAILED_IN_BILLING,
    GROUP_COMPLETED, GROUP_FAILED, FINISHED
}

data class JobState(var jobId: UUID,
                    var state: State,
                    var numGroupsTotal: Int,
                    var numGroupsPricing: Int,
                    var numGroupssPricingFailed: Int,
                    var numGroupsBilling: Int,
                    var numGroupsBillingFailed: Int,
                    var numGroupsComplete: Int,
                    var failedGroupIds: List<UUID>,
                    var started: LocalDateTime,
                    var finished: LocalDateTime?
    ) {
        constructor(jobId: UUID, numGroupsTotal: Int, numGroupsPricing: Int):
                this(jobId, State.STARTED, numGroupsTotal, numGroupsPricing, 0, 0, 0, 0, emptyList(),
                LocalDateTime.now(), null)
        constructor():
                this(UUID.randomUUID(), State.STARTED, 0, 0, 0, 0, 0, 0, emptyList(),
                LocalDateTime.now(), null)

    enum class State {
        STARTED, COMPLETED
    }
}

data class GroupState(val jobId: UUID,
                      val groupId: UUID,
                      var state: State,
                      var numContractsTotal: Int,
                      var numContractsPricing: Int,
                      var numContractsPricingFailed: Int,
                      var numContractsBilling: Int,
                      var numContractsBillingFailed: Int,
                      var numContractsComplete: Int,
                      var failedContractIds: List<UUID>,
                      var started: LocalDateTime,
                      var finished: LocalDateTime?
) {
        constructor(jobId: UUID, groupId: UUID, numContractsTotal: Int, numContractsPricing: Int):
                this(jobId, groupId, State.STARTED, numContractsTotal, numContractsPricing, 0, 0, 0, 0, emptyList(),
                LocalDateTime.now(), null)
    enum class State {
        STARTED, COMPLETED
    }
}

data class ContractState(val jobId: UUID, val groupId: UUID, val contractId: UUID, val state: State, val contract: Contract) {
    enum class State {
        SENT_FOR_PRICING, FAILED_IN_PRICING, SENT_FOR_BILLING, FAILED_IN_BILLING, SUCCESSFUL
    }
}

data class Group(val jobId: UUID, val id: UUID, val contracts: List<Contract>, val started: LocalDateTime = LocalDateTime.now())

data class Contract(val id: UUID, val basePeriodsToPrice: List<Period>, val periodsToBill: List<Period>)

data class Period(val from: LocalDate, val to: LocalDate)
