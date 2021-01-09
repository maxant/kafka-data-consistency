package ch.maxant.kdc.mf.billing.boundary

import ch.maxant.kdc.mf.billing.control.Action
import ch.maxant.kdc.mf.billing.control.BillingService
import ch.maxant.kdc.mf.billing.control.Event
import ch.maxant.kdc.mf.billing.control.EventService
import ch.maxant.kdc.mf.library.Context
import ch.maxant.kdc.mf.library.KafkaHandler
import ch.maxant.kdc.mf.library.MessageBuilder
import ch.maxant.kdc.mf.library.PimpedAndWithDltAndAck
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.jboss.logging.Logger
import java.math.BigDecimal
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

        @Inject
        var eventService: EventService,

        @Inject
        var billingService: BillingService,

        @Inject
        var messageBuilder: MessageBuilder,

        @ConfigProperty(name = "ch.maxant.kdc.mf.billing.failRandomlyForTestingPurposes", defaultValue = "false")
        var failRandomlyForTestingPurposes: Boolean

) : KafkaHandler {

    @Inject
    @Channel("billing-commands-out")
    lateinit var billingCommands: Emitter<String>

    @Inject
    @Channel("contracts-event-bus-out")
    lateinit var contractEventBus: Emitter<String>

    private val log = Logger.getLogger(this.javaClass)

    private val random = Random()

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
            READ_PRICE_GROUP -> sendToPricingToReadPrices(record.value())
            RECALCULATE_PRICE_GROUP -> sendToPricingToRecalculatePrices(record.value())
            BILL_GROUP -> bill(record.value())
            else -> TODO("unknown message ${context.event}")
        }
    }

    fun sendToPricingToReadPrices(value: String) {
        val group = om.readValue<Group>(value)
        sendToPricingToReadPrices(group)
    }

    fun sendToPricingToReadPrices(group: Group) {
        log.info("reading prices for group ${group.groupId} from job ${group.jobId}")
asdf
        val commands = mutableListOf<PricingCommand>()
        for(contract in group.contracts) {
            for(period in contract.basePeriodsToPrice) {
                commands.add(PricingCommand(contract.contractId, period.from, period.to))
            }
        }

        val fail = failRandomlyForTestingPurposes && random.nextInt(100) == 1
        if(fail) {
            log.warn("failing job ${group.jobId} and group ${group.groupId} for testing purposes at pricing!")
        }
        sendCommand(PricingCommandGroup(group.jobId, group.groupId, commands, fail))

        group.contracts.forEach { sendEvent_SentToPricing(it) }
    }

    fun sendToPricingToRecalculatePrices(value: String) {
        val group = om.readValue<Group>(value)
        sendToPricingToRecalculatePrices(group)
    }

    fun sendToPricingToRecalculatePrices(group: Group) {
        log.info("recalculating prices for group ${group.groupId} from job ${group.jobId}")

        val commands = mutableListOf<PricingCommand>()
        for(contract in group.contracts) {
            for(period in contract.basePeriodsToPrice) {
                commands.add(PricingCommand(contract.contractId, period.from, period.to))
            }
        }

        val fail = failRandomlyForTestingPurposes && random.nextInt(100) == 1
        if(fail) {
            log.warn("failing job ${group.jobId} and group ${group.groupId} for testing purposes at pricing!")
        }
        sendCommand(PricingCommandGroup(group.jobId, group.groupId, commands, fail))

        group.contracts.forEach { sendEvent_SentToPricing(it) }
    }

    fun bill(value: String) {
        val group = om.readValue<Group>(value)
        bill(group)
    }

    fun bill(group: Group) {
        log.info("billing group ${group.groupId} from job ${group.jobId}")

        try {
            billingService.billGroup(group)
            group.contracts.forEach { sendEvent_SentToBilling(it) }
        } catch (e: Exception) {
            if(group.contracts.size == 1) {
                log.error("failed to bill contract as part of group ${group.groupId} in job ${group.jobId}, " +
                        "with contractId ${group.contracts[0].contractId}", e)
                sendEvent_FailedToBillContract(group.contracts[0])
            } else {
                log.info("failed to bill group => sending individually ${group.groupId}")
                // resend individually
                group.contracts.forEach { contract ->
                    val newGroupId = UUID.randomUUID() // we create a new group - one for each individual contract, containing the periods to price
                    val newGroup = Group(group.jobId, newGroupId, listOf(contract))

                    billingCommands.send(messageBuilder.build(newGroup.jobId, newGroup, command = BILL_GROUP))
                    log.info("published bill group command")
                }
            }
        }
    }

    private fun sendEvent_SentToPricing(contract: Contract) {
        eventService.sendEvent(Event(Action.SENT_TO_PRICING, contract.jobId, contract.groupId, contract.contractId, contract))
    }

    private fun sendEvent_SentToBilling(contract: Contract) {
        eventService.sendEvent(Event(Action.SENT_TO_BILLING, contract.jobId, contract.groupId, contract.contractId, contract))
    }

    fun sendEvent_FailedToPriceContract(contract: Contract) {
        eventService.sendEvent(Event(Action.FAILED_IN_PRICING, contract.jobId, contract.groupId, contract.contractId, contract))
    }

    fun sendEvent_FailedToBillContract(contract: Contract) {
        eventService.sendEvent(Event(Action.FAILED_IN_BILLING, contract.jobId, contract.groupId, contract.contractId, contract))
    }

    private fun sendCommand(pricingCommandGroup: PricingCommandGroup) {
        contractEventBus.send(messageBuilder.build(pricingCommandGroup.groupId, pricingCommandGroup, command = "CALCULATE_PRICES_FOR_GROUP_OF_CONTRACTS"))
        log.info("published pricing command groupId ${pricingCommandGroup.groupId} with contractIds ${pricingCommandGroup.commands.map {it.contractId}}")
    }

    companion object {
        const val READ_PRICE_GROUP = "READ_PRICE_GROUP"
        const val RECALCULATE_PRICE_GROUP = "RECALCULATE_PRICE_GROUP"
        const val BILL_GROUP = "BILL_GROUP"
    }
}

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// command to billing, in order to kick off a billing run for a group of contracts
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
data class Group(val jobId: UUID, val groupId: UUID, val contracts: List<Contract>, val started: LocalDateTime = LocalDateTime.now())

data class Contract(val jobId: UUID, val groupId: UUID, val contractId: UUID, val billingDefinitionId: String, val basePeriodsToPrice: List<Period>, val periodsToBill: List<Period>)

data class Period(val from: LocalDate, val to: LocalDate, var price: BigDecimal = BigDecimal.ZERO)

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// pricing command => sent to pricing
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
data class PricingCommandGroup(val jobId: UUID, val groupId: UUID, val commands: List<PricingCommand>, var failForTestingPurposes: Boolean)

data class PricingCommand(val contractId: UUID, val from: LocalDate, val to: LocalDate)

