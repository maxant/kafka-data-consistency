package ch.maxant.kdc.mf.pricing.boundary

import ch.maxant.kdc.mf.library.*
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
class Consumer(
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
    private lateinit var pricingResultEvent: javax.enterprise.event.Event<PricingResult>

    @Inject
    private lateinit var pricingCommandGroupResultEvent: javax.enterprise.event.Event<PricingCommandGroupResult>

    private val log = Logger.getLogger(this.javaClass)

    override fun getKey() = "event-bus-in"

    override fun getRunInParallel() = true

    @PimpedAndWithDltAndAck
    override fun handle(record: ConsumerRecord<String, String>) {
        when (context.event) {
            "CREATED_DRAFT", "UPDATED_DRAFT" -> {
                try {
                    log.info("pricing draft")
                    val value = om.readTree(record.value())
                    val result = pricingService.priceDraft(value)
                    sendEvent(result)
                } catch (e: Exception) {
                    log.error("FAILED TO PRICE", e)
                }
            }
            "CALCULATE_PRICES_FOR_GROUP_OF_CONTRACTS" -> {
                try {
                    log.info("pricing contract")
                    val result = pricingService.priceContract(record.value())
                    sendEvent(result)
                } catch (e: Exception) {
                    val group = om.readValue<PricingCommandGroup>(record.value())
                    log.error("FAILED TO PRICE GROUP ${group.groupId} in job ${group.jobId} " +
                            "with contractIds ${group.commands.map { it.contractId }.distinct()} => " +
                            "publishing failed event", e)
                    val commands = group.commands.map { PricingCommandResult(it.contractId, failed = true) }
                    sendEvent(PricingCommandGroupResult(group.groupId, commands))
                }
            }
            else -> {
                // ignore other messages
                log.info("skipping irrelevant message ${context.event}")
            }
        }
    }

    private fun sendEvent(prices: PricingResult) {
        pricingResultEvent.fire(prices)
    }

    @SuppressWarnings("unused")
    private fun send(@Observes(during = TransactionPhase.AFTER_SUCCESS) prices: PricingResult) {
        // TODO transactional outbox
        eventBus.send(messageBuilder.build(prices.contractId, prices, event = "UPDATED_PRICES"))
        log.info("published prices for contractId ${prices.contractId}")
    }

    private fun sendEvent(prices: PricingCommandGroupResult) {
        pricingCommandGroupResultEvent.fire(prices)
    }

    @SuppressWarnings("unused")
    private fun send(@Observes(during = TransactionPhase.AFTER_SUCCESS) prices: PricingCommandGroupResult) {
        // TODO transactional outbox
        eventBus.send(messageBuilder.build(prices.groupId, prices, event = "UPDATED_PRICES_FOR_GROUP_OF_CONTRACTS"))
        log.info("published prices group for contractIds ${prices.commands.map { it.contractId }.distinct()}")
    }
}
