package ch.maxant.kdc.mf.pricing.boundary

import ch.maxant.kdc.mf.library.Context
import ch.maxant.kdc.mf.library.KafkaHandler
import ch.maxant.kdc.mf.library.MessageBuilder
import ch.maxant.kdc.mf.library.PimpedAndWithDltAndAck
import ch.maxant.kdc.mf.pricing.control.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.jboss.logging.Logger
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.Observes
import javax.enterprise.event.TransactionPhase
import javax.inject.Inject

@ApplicationScoped
@SuppressWarnings("unused")
class PricingConsumer(
        @Inject
        var om: ObjectMapper,

        @Inject
        var pricingService: PricingService,

        @Inject
        var context: Context,

        @Inject
        var messageBuilder: MessageBuilder

) : KafkaHandler {

    @Inject
    @Channel("event-bus-out")
    lateinit var eventBus: Emitter<String>

    @Inject
    private lateinit var pricingDraftResultEvent: javax.enterprise.event.Event<PricingResult>

    @Inject
    private lateinit var pricingContractResultEvent: javax.enterprise.event.Event<PricingCommandGroupResult>

    private val log = Logger.getLogger(this.javaClass)

    override fun getKey() = "event-bus-in"

    override fun getRunInParallel() = true

    @PimpedAndWithDltAndAck
    override fun handle(record: ConsumerRecord<String, String>) {
        var unhandled = false
        when (context.event) {
            "ADDED_DSC_FOR_DRAFT" -> priceDraft(record)
            else -> unhandled = true
        }
        when (context.command) {
            "READ_PRICES_FOR_GROUP_OF_CONTRACTS" -> readPricesForGroupOfContracts(record)
            "RECALCULATE_PRICES_FOR_GROUP_OF_CONTRACTS" -> recalculatePricesForGroupOfContracts(record)
            else -> unhandled = true
        }
        if(unhandled) {
            // ignore other messages
            log.info("skipping irrelevant message ${context.event}")
        }
    }

    private fun priceDraft(record: ConsumerRecord<String, String>) {
        log.info("pricing draft")
        val value = om.readTree(record.value())
        val result = pricingService.priceDraft(value)
        sendEvent(result)
    }

    private fun readPricesForGroupOfContracts(record: ConsumerRecord<String, String>) {
        val group = om.readValue<PricingCommandGroup>(record.value())
        try {
            log.info("reading prices for group of contracts")
            val result = pricingService.readPrices(group)
            sendEvent(result)
        } catch (e: Exception) {
            val msg = "FAILED TO READ PRICE FOR GROUP ${group.groupId} " +
                    "with contractIds ${group.commands.map { it.contractId }.distinct()} => " +
                    "publishing failed event"
            log.error(msg, e)
            val commands = group.commands.map { PricingCommandResult(it.contractId) }
            sendEvent(PricingCommandGroupResult(group.groupId, commands,
                recalculated = false,
                failed = true,
                failedReason = "$msg: ${e.message}"
            ))
        }
    }

    private fun recalculatePricesForGroupOfContracts(record: ConsumerRecord<String, String>) {
        val group = om.readValue<PricingCommandGroup>(record.value())
        try {
            log.info("repricing group of contracts")
            val result = pricingService.repriceContract(group)
            sendEvent(result)
        } catch (e: Exception) {
            val msg = "FAILED TO REPRICE GROUP ${group.groupId} " +
                    "with contractIds ${group.commands.map { it.contractId }.distinct()} => " +
                    "publishing failed event"
            log.error(msg, e)
            val commands = group.commands.map { PricingCommandResult(it.contractId) }
            sendEvent(PricingCommandGroupResult(group.groupId, commands,
                recalculated = true,
                failed = true,
                failedReason = "$msg: ${e.message}"
            ))
        }
    }

    private fun sendEvent(prices: PricingResult) {
        pricingDraftResultEvent.fire(prices)
    }

    @SuppressWarnings("unused")
    private fun send(@Observes(during = TransactionPhase.AFTER_SUCCESS) prices: PricingResult) {
        // TODO transactional outbox
        eventBus.send(messageBuilder.build(prices.contractId, prices, event = "UPDATED_PRICES_FOR_DRAFT"))
        log.info("published prices for contractId ${prices.contractId}")
    }

    private fun sendEvent(prices: PricingCommandGroupResult) {
        this.pricingContractResultEvent.fire(prices)
    }

    @SuppressWarnings("unused")
    private fun send(@Observes(during = TransactionPhase.AFTER_SUCCESS) prices: PricingCommandGroupResult) {
        // TODO transactional outbox
        val event = if(prices.recalculated) "RECALCULATED_PRICES_FOR_GROUP_OF_CONTRACTS" else "READ_PRICES_FOR_GROUP_OF_CONTRACTS"
        eventBus.send(messageBuilder.build(prices.groupId, prices, event = event))
        log.info("published prices group for contractIds ${prices.commands.map { it.contractId }.distinct()}")
    }
}
