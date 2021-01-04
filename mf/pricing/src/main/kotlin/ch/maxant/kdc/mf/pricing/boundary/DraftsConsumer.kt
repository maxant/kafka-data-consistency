package ch.maxant.kdc.mf.pricing.boundary

import ch.maxant.kdc.mf.library.*
import ch.maxant.kdc.mf.pricing.control.PricingResult
import ch.maxant.kdc.mf.pricing.control.PricingService
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.eclipse.microprofile.context.ManagedExecutor
import org.eclipse.microprofile.context.ThreadContext
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.jboss.logging.Logger
import java.util.concurrent.CompletableFuture
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
        @WithFreshContext
        var managedExecutor: ManagedExecutor,

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

    @PimpedAndWithDltAndAck
    override fun handle(record: ConsumerRecord<String, String>) {
        val contextCopy = Context.of(context)
        val draft = om.readTree(record.value())
        when (context.event) {
            "CREATED_DRAFT", "UPDATED_DRAFT" -> {
                managedExecutor.supplyAsync { // NOTE: this executor has thie @WithFreshContext annotation!! Without that, we get problems with lost messages! prolly coz scope is overwritten by contending threads
                    try {
                        // since we're running on a new thread with no context, lets copy the context across
                        // NOTE: we dont want to use the same context as before, because the request scoped beans
                        // are NOT dependent on the thread, rather on the request and quarkus won't see this as a new
                        // request! see https://quarkusio.zulipchat.com/#narrow/stream/187030-users/topic/Does.20.40RequestScoped.20only.20work.20with.20web.20requests.3F
                        // this is kind of like when we use @Asynchronous with EJBs and we need to copy the request
                        // scoped state manually into the new context running async.
                        // its not clear to me when the request scope is created and cleared up. certainly when a
                        // web request comes in. probably from the point at which the request scoped bean is
                        // instantiated to the point in time when that frame is popped from the stack, but that
                        // is unclear for async stuff!
                        context.setup(contextCopy)

                        log.info("pricing draft")
                        val result = pricingService.priceDraft(draft)
                        sendEvent(result)
                    } catch (e: Exception) {
                        log.error("FAILED TO PRICE", e)
                    }
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
        // since this is happening async after the transaction, and we don't return anything,
        // we just pass a new CompletableFuture and don't care what happens with it
        eventBus.send(messageBuilder.build(prices.contractId, prices, CompletableFuture(), event = "UPDATED_PRICES"))
        log.info("published prices for contractId ${prices.contractId}")
    }
}