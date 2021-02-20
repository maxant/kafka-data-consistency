package ch.maxant.kdc.mf.dsc.boundary

import ch.maxant.kdc.mf.dsc.control.DiscountSurchargeService
import ch.maxant.kdc.mf.library.Context
import ch.maxant.kdc.mf.library.KafkaHandler
import ch.maxant.kdc.mf.library.MessageBuilder
import ch.maxant.kdc.mf.library.PimpedAndWithDltAndAck
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
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
class DscConsumer(
        @Inject
        var om: ObjectMapper,

        @Inject
        var discountSurchargeService: DiscountSurchargeService,

        @Inject
        var context: Context,

        @Inject
        var messageBuilder: MessageBuilder

) : KafkaHandler {

    @Inject
    @Channel("event-bus-out")
    lateinit var eventBus: Emitter<String>

    @Inject
    private lateinit var event: javax.enterprise.event.Event<JsonNode>

    private val log = Logger.getLogger(this.javaClass)

    override fun getKey() = "event-bus-in"

    override fun getRunInParallel() = true

    @PimpedAndWithDltAndAck
    override fun handle(record: ConsumerRecord<String, String>) {
        var unhandled = false
        when (context.event) {
            "CREATED_DRAFT", "UPDATED_DRAFT" -> handleDraft(record)
            else -> unhandled = true
        }
        if(unhandled) {
            // ignore other messages
            log.info("skipping irrelevant message ${context.event}")
        }
    }

    private fun handleDraft(record: ConsumerRecord<String, String>) {
        try {
            log.info("handling draft")
            val model = om.readTree(record.value())
            discountSurchargeService.handleDraft(model)
            sendEvent(model)
        } catch (e: Exception) {
            log.error("FAILED TO PRICE", e)
        }
    }

    private fun sendEvent(model: JsonNode) {
        event.fire(model)
    }

    @SuppressWarnings("unused")
    private fun send(@Observes(during = TransactionPhase.AFTER_SUCCESS) model: JsonNode) {
        // TODO transactional outbox
        val contractId = model.get("contract").get("id").asText()
        eventBus.send(messageBuilder.build(contractId, model, event = "ADDED_DSC_FOR_DRAFT"))
        log.info("published DSC for contractId $contractId")
    }
}
