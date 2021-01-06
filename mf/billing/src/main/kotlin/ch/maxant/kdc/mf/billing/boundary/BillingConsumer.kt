package ch.maxant.kdc.mf.billing.boundary

import ch.maxant.kdc.mf.billing.dto.SelectionDto
import ch.maxant.kdc.mf.library.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.common.annotations.VisibleForTesting
import io.smallrye.reactive.messaging.kafka.OutgoingKafkaRecordMetadata
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.reactive.messaging.Message
import org.jboss.logging.Logger
import java.time.LocalDate
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
        var messageBuilder: MessageBuilder

) : KafkaHandler {

    @Inject
    @Channel("contracts-out")
    lateinit var contractEventBus: Emitter<String>

    @Inject
    @Channel("internal-state-out")
    lateinit var internalState: Emitter<String>

    private val log = Logger.getLogger(this.javaClass)

    override fun getKey() = "billing-commands"

    override fun getRunInParallel() = true

    /**
     * this is the general entry point into the billing application. selection is either done because a contract is
     * approved, or because the scheduled recurring billing process has found contracts that are not yet billed.
     * not using kafka streams api here, because we need easy access to headers.
     */
    @PimpedAndWithDltAndAck
    override fun handle(record: ConsumerRecord<String, String>) {
        when (context.command) {
            SELECTED_FOR_BILLING -> groupAndSendForPricing(record.value())
            else ->
                // ignore other messages
                log.info("skipping irrelevant message ${context.event}")
        }
    }

    @VisibleForTesting
    fun groupAndSendForPricing(value: String) {
        log.info("selected for billing")
        val selection = om.readValue<SelectionDto>(value)

        publishInternalState("selection::${selection.id}", SelectionState.STARTED.toString())

        var commands = mutableListOf<PricingCommand>()
        for((i, contract) in selection.contracts.withIndex()) {
            if(i % groupSize == 0) {
                send(PricingCommandGroup(selection.id, commands))
                commands = mutableListOf()
            }
            commands.addAll(contract.basePeriodsToPrice.map { PricingCommand(contract.id, it.from, it.to) })

            publishInternalState("selection::${selection.id}%%contract::${contract.id}", ContractState.SENT_FOR_PRICING.toString())
        }

        // send last group if necessary
        if(commands.isNotEmpty()) {
            send(PricingCommandGroup(selection.id, commands))
        }
    }

    private fun publishInternalState(key: String, value: String) {
        val metadata = OutgoingKafkaRecordMetadata.builder<String>()
                .withKey(key)
                .build()
        internalState.send(Message.of(value).addMetadata(metadata))
    }

    private fun send(pricingCommandGroup: PricingCommandGroup) {
        // TODO transactional outbox
        contractEventBus.send(messageBuilder.build(pricingCommandGroup.groupId, pricingCommandGroup, command = "CALCULATE_PRICES"))
        log.info("published pricing command groupId ${pricingCommandGroup.groupId} with contractIds ${pricingCommandGroup.contents.map {it.contractId}}")
    }

    companion object {
        const val SELECTED_FOR_BILLING = "SELECTED_FOR_BILLING"
    }
}

abstract class Group<T>(val selectionId: UUID, val contents: List<T>, val groupId: UUID = UUID.randomUUID())

class PricingCommandGroup(selectionId: UUID, commands: List<PricingCommand>): Group<PricingCommand>(selectionId, commands)

data class PricingCommand(val contractId: UUID, val from: LocalDate, val to: LocalDate)

enum class SelectionState {
    STARTED, COMPLETED
}

enum class ContractState {
    SENT_FOR_PRICING, FAILED_IN_PRICING, SENT_FOR_BILLING, FAILED_IN_BILLING, SUCCESSFUL
}