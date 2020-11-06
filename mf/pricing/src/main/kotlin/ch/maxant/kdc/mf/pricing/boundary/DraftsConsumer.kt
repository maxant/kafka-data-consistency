package ch.maxant.kdc.mf.pricing.boundary

import ch.maxant.kdc.mf.library.*
import ch.maxant.kdc.mf.pricing.control.PricingResult
import ch.maxant.kdc.mf.pricing.control.PricingService
import ch.maxant.kdc.mf.pricing.definitions.Price
import com.fasterxml.jackson.databind.ObjectMapper
import io.smallrye.reactive.messaging.kafka.OutgoingKafkaRecordMetadata
import org.apache.kafka.common.header.internals.RecordHeader
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.eclipse.microprofile.reactive.messaging.Message
import org.jboss.logging.Logger
import java.util.*
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.CompletionStage
import javax.enterprise.context.ApplicationScoped
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
) {
    @Inject
    @Channel("event-bus-out")
    lateinit var eventBus: Emitter<String>

    private val log = Logger.getLogger(this.javaClass)

    @Incoming("event-bus-in")
    @Transactional
    @PimpedAndWithDltAndAck
    fun process(msg: Message<String>): CompletionStage<*> {
        val draft = om.readTree(msg.payload)
        return when (context.event) {
            "DRAFT_CREATED" -> {
                log.info("pricing draft")
                pricingService
                    .priceDraft(draft)
                    .thenApply {
                        sendEvent(it)
                    }
            }
            else -> completedFuture(Unit) // ignore other messages
        }
    }

    private fun sendEvent(prices: PricingResult) {
        eventBus.send(messageBuilder.build(prices.contractId, prices, event = "UPDATED_PRICES"))
    }

}
