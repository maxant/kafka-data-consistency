package ch.maxant.kdc.mf.pricing.boundary

import ch.maxant.kdc.mf.library.PimpedAndWithDltAndAck
import ch.maxant.kdc.mf.library.REQUEST_ID
import ch.maxant.kdc.mf.library.getRequestId
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
        var pricingService: PricingService
) {
    @Inject
    @Channel("event-bus-out")
    lateinit var eventBus: Emitter<String>

    private val log = Logger.getLogger(this.javaClass)

    @Incoming("event-bus-in")
    @Transactional
    @PimpedAndWithDltAndAck
    fun process(msg: Message<String>): CompletionStage<*> {
        val event = om.readTree(msg.payload)
        val name = event.get("event").asText()
        return when (name) {
            "DRAFT_CREATED" -> {
                log.info("pricing draft")
                pricingService
                    .priceDraft(event)
                    .thenApply {
                        sendEvent(PublishedPricesEvent(getRequestId(msg), it))
                    }
            }
            else -> completedFuture(Unit) // ignore other message types
        }
    }

    private fun sendEvent(event: PublishedPricesEvent) {
        val metadata = OutgoingKafkaRecordMetadata.builder<Any>()
                .withKey(event.contractId)
                .withHeaders(listOf(RecordHeader(REQUEST_ID, event.requestId.toByteArray())))
                .build()
        eventBus.send(Message.of(om.writeValueAsString(event)).addMetadata(metadata))
    }

}

private data class PublishedPricesEvent(
        val requestId: String,
        val contractId: String,
        val value: Map<UUID, Price>,
        val event: String = "UPDATED_PRICES"
) {
    constructor(requestId: String, priceResult: PricingResult) :
            this(requestId, priceResult.contractId.toString(), priceResult.prices)
}