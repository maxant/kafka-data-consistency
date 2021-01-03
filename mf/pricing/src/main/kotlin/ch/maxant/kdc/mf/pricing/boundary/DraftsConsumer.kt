package ch.maxant.kdc.mf.pricing.boundary

import ch.maxant.kdc.mf.library.*
import ch.maxant.kdc.mf.pricing.control.PricingResult
import ch.maxant.kdc.mf.pricing.control.PricingService
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.reactive.messaging.Message
import org.jboss.logging.Logger
import java.lang.IllegalStateException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.Observes
import javax.enterprise.event.TransactionPhase
import javax.inject.Inject
import javax.transaction.Transactional

@ApplicationScoped
@SuppressWarnings("unused")
class DraftsConsumer(
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

    private val log = Logger.getLogger(this.javaClass)

    override fun getTopic() = "event-bus"

    @Transactional
    @PimpedAndWithDltAndAck
    override fun handle(record: ConsumerRecord<String, String>) {
        val draft = om.readTree(record.value())
        return when (context.event) {
            "CREATED_DRAFT", "UPDATED_DRAFT" -> {
                log.info("pricing draft")
                val result = pricingService.priceDraft(draft)
                sendEvent(result)
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
        // since this is happening async after the transaction, and we don't return anything,
        // we just pass a new CompletableFuture and don't care what happens with it
        eventBus.send(messageBuilder.build(prices.contractId, prices, CompletableFuture(), event = "UPDATED_PRICES"))
        withMdcSet(context) {
            log.info("published prices for contractId ${prices.contractId}")
        }
    }
}