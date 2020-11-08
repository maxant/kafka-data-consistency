package ch.maxant.kdc.mf.pricing.boundary

import ch.maxant.kdc.mf.library.Context
import ch.maxant.kdc.mf.library.MessageBuilder
import ch.maxant.kdc.mf.library.PimpedAndWithDltAndAck
import ch.maxant.kdc.mf.pricing.control.PricingResult
import ch.maxant.kdc.mf.pricing.control.PricingService
import com.fasterxml.jackson.databind.ObjectMapper
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.eclipse.microprofile.reactive.messaging.Message
import org.jboss.logging.Logger
import java.util.concurrent.CompletableFuture
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
            "CREATED_DRAFT" -> {
                log.info("pricing draft")
                pricingService
                    .priceDraft(draft)
                    .thenCompose {
                        sendEvent(it)
                    }
            }
            else -> completedFuture(Unit) // ignore other messages
        }
    }

    private fun sendEvent(prices: PricingResult): CompletableFuture<Unit> {
        val ack = CompletableFuture<Unit>()
        eventBus.send(messageBuilder.build(prices.contractId, prices, ack, event = "UPDATED_PRICES"))
        return ack
    }

}
