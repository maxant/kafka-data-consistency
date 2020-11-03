package ch.maxant.kdc.mf.pricing.boundary

import ch.maxant.kdc.mf.library.ErrorsHandled
import ch.maxant.kdc.mf.pricing.definitions.Price
import ch.maxant.kdc.mf.pricing.definitions.Prices
import ch.maxant.kdc.mf.pricing.dto.Component
import ch.maxant.kdc.mf.pricing.dto.Visitor
import ch.maxant.kdc.mf.pricing.entity.PriceEntity
import com.fasterxml.jackson.databind.ObjectMapper
import io.smallrye.reactive.messaging.annotations.Blocking
import io.smallrye.reactive.messaging.kafka.OutgoingKafkaRecordMetadata
import org.apache.kafka.common.header.internals.RecordHeader
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.eclipse.microprofile.reactive.messaging.Message
import org.jboss.logging.Logger
import java.time.LocalDateTime
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.persistence.EntityManager
import javax.transaction.Transactional
import kotlin.collections.HashMap

@ApplicationScoped
class DraftsConsumer(
        @Inject
        var em: EntityManager,

        @Inject
        var om: ObjectMapper
) {
    @Inject
    @Channel("event-bus-out")
    lateinit var eventBus: Emitter<String>

    private val log = Logger.getLogger(this.javaClass)

    /* FIXME
    I'd like to have the requestId in a Kakfa Record Header, but I can't get the APIs to work nicely.
    If I use his method as follows, I cannot use @Blocking and have to hand the JPA part off using
    managedExecutor and threadContext, and I end up with this Hibernate warning and the whole thing freezes:
    2020-11-03 23:05:21,924 WARN  [org.hib.res.tra.bac.jta.int.syn.SynchronizationCallbackCoordinatorTrackingImpl]
        (executor-thread-2) HHH000451: Transaction afterCompletion called by a background thread; delaying
        afterCompletion processing until the original thread can handle it. [status=4]

    @Incoming("event-bus-in")
    @Transactional
    @ErrorsHandled
    fun process(msg: Message<String>): CompletionStage<Void>? {
        val r: ()->Unit = {
            try {
                _price(msg)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return managedExecutor.runAsync(threadContext.contextualRunnable(r))
        .thenComposeAsync(Function<Void, CompletionStage<Void>> {
            msg.ack()
        }, managedExecutor)
    }
    */

    @Incoming("event-bus-in")
    @Transactional
    @Blocking
    @ErrorsHandled
    fun process(msg: String) {
        val event = om.readTree(msg)
        val requestId = event.get("requestId").textValue()
        val name = event.get("event").asText()
        if("DRAFT_CREATED" == name) {
            log.info("HERE3a")
            // TODO add extension method to make this fetchable via a path => ur use JsonPath?
            val value = event.get("value")
            val contractId = UUID.fromString(value.get("contract").get("id").asText())
            val pack = value.get("pack").toString()
            val start = LocalDateTime.parse(value.get("contract").get("start").asText())
            val end = LocalDateTime.parse(value.get("contract").get("end").asText())
            log.info("Pricing contract $contractId: $value")

            val root = om.readValue(pack, Component::class.java)

            val prices = price(contractId, start, end, root)

            sendEvent(PublishedPricesEvent(requestId, contractId.toString(), prices))
        } // else ignore other message types
    }

    private fun sendEvent(event: PublishedPricesEvent) {
        val metadata = OutgoingKafkaRecordMetadata.builder<Any>()
                .withKey(event.contractId)
                .withHeaders(listOf(RecordHeader("requestId", event.requestId.toByteArray())))
                .build()
        eventBus.send(Message.of(om.writeValueAsString(event)).addMetadata(metadata))
    }

    private fun price(contractId: UUID, start: LocalDateTime, end: LocalDateTime, root: Component): Map<UUID, Price> {
        val prices = HashMap<UUID, Price>()
        root.accept(object: Visitor {
            override fun visit(component: Component) {
                val componentId = UUID.fromString(component.componentId)

                val rule = Prices.findRule(component)

                val price = rule(component)
                prices[componentId] = price

                val ruleName = rule.javaClass.name.substring(rule.javaClass.name.indexOf("$")+1)
                log.info("priced component ${component.componentDefinitionId}: $price using rule $ruleName")

                val pe = PriceEntity(UUID.randomUUID(), contractId, start, end,
                        componentId, ruleName, price.total, price.tax)

                em.persist(pe)
            }
        })
        return prices

        /*
{"draft":
    {"contract":
        { "id":"82e49c2d-24e3-426b-b20d-b5691f7e44b6",
          "start":"2020-10-26T00:00:00","end":"2022-10-16T00:00:00","state":"DRAFT"},
        "pack":
            { "componentDefinitionId":"CardboardBox",
              "componentId": "<aUuid>"
              "configs":[
                {"name":"SPACES","value":10,"units":"NONE","type":"int"},
                {"name":"QUANTITY","value":10,"units":"PIECES","type":"int"},
                {"name":"MATERIAL","value":"CARDBOARD","units":"NONE","type":"ch.maxant.kdc.mf.contracts.definitions.Material"}],
                "children":[
                    {"productId":"COOKIES_MILKSHAKE","componentDefinitionId":"Milkshake",
        */
    }

}

private data class PublishedPricesEvent(
        val requestId: String,
        val contractId: String,
        val value: Map<UUID, Price>,
        val event: String = "UPDATED_PRICES"
)