package ch.maxant.kdc.mf.pricing.boundary

import ch.maxant.kdc.mf.library.Context
import ch.maxant.kdc.mf.library.MessageBuilder
import ch.maxant.kdc.mf.library.PimpedAndWithDltAndAck
import ch.maxant.kdc.mf.library.withMdcSet
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
        var context: Context
) {
    private val log = Logger.getLogger(this.javaClass)

    // @Incoming("event-bus-in")
    @Transactional
    @PimpedAndWithDltAndAck
    fun process(msg: Message<String>): CompletionStage<*> {
        val draft = om.readTree(msg.payload)
        return when (context.event) {
            "CREATED_DRAFT", "UPDATED_DRAFT" -> {
                log.info("pricing draft")
                pricingService
                    .priceDraft(draft)
                        /*
//                        TODO i think this is where the shit happens. the context wont be the same, and might be randomly picked up
                    .thenCompose {


                        sendEvent(it)
                    }
                    */
            }
            else -> {
                // ignore other messages
                log.info("skipping irrelevant message ${context.event}")
                completedFuture(Unit)
            }
        }
    }
}
