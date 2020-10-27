package ch.maxant.kdc.mf.pricing.boundary

import ch.maxant.kdc.mf.pricing.definitions.Price
import ch.maxant.kdc.mf.pricing.definitions.Prices
import ch.maxant.kdc.mf.pricing.dto.Component
import ch.maxant.kdc.mf.pricing.dto.Visitor
import ch.maxant.kdc.mf.pricing.entity.PriceEntity
import com.fasterxml.jackson.databind.ObjectMapper
import io.smallrye.reactive.messaging.annotations.Blocking
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.jboss.logging.Logger
import java.time.LocalDateTime
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.persistence.EntityManager
import javax.transaction.Transactional
import kotlin.collections.HashMap

@ApplicationScoped
class OffersSink(
        @Inject
        var em: EntityManager,

        @Inject
        var om: ObjectMapper
) {
    @Inject
    @Channel("event-bus-out")
    lateinit var eventBus: Emitter<String>

    val log = Logger.getLogger(this.javaClass)

    @Incoming("event-bus-in")
    @Blocking
    @Transactional
    //TODO how to access the kafka record?
    // fun process(msg: Message<String>): CompletionStage<*> {
    fun process(msg: String) {
        try {
            val event = om.readTree(msg)
            val name = event.get("event")
            if("OFFER_CREATED" == name.asText()) {
                val value = event.get("value")
                val pack = value.get("pack").toString()
                val contractId = UUID.fromString(value.get("contract").get("id").asText())
                val start = LocalDateTime.parse(value.get("contract").get("start").asText())
                val end = LocalDateTime.parse(value.get("contract").get("end").asText())
                log.info("\r\nPricing an offer $contractId: ${value}")

                val root = om.readValue(pack, Component::class.java)

                priceOffer(contractId, start, end, root)
            } else {
                log.info("ignoring message of type ${event}")
            }
        }catch (e: Exception) {
            log.error("failed to process message $msg", e) // TODO replace with DLT and suitable error processing
        }
        /*
        println("GOT ONE: ${msg.payload}")
        val metadata = msg.getMetadata(IncomingKafkaRecordMetadata::class.java)
        if(metadata.isPresent) {
            println("metadata: ofset: ${metadata.get().offset} / key: ${metadata.get().key} / topic: ${metadata.get().topic} / partition: ${metadata.get().partition}")
        } else {
            println("no metadata")
        }

        return msg.ack()
         */
    }

    private fun priceOffer(contractId: UUID, start: LocalDateTime, end: LocalDateTime, root: Component) {
        val prices = HashMap<UUID, Price>()
        root.accept(object: Visitor {
            override fun visit(component: Component) {
                val componentId = UUID.fromString(component.componentId)

                val rule = Prices.findRule(component)

                val price = rule(component)
                prices.put(componentId, price)

                val ruleName = rule.javaClass.name.substring(rule.javaClass.name.indexOf("$")+1)
                log.info("priced component ${component.componentDefinitionId}: $price using rule $ruleName")

                val pe = PriceEntity(UUID.randomUUID(), contractId, start, end,
                        componentId, ruleName, price.total, price.tax)

                em.persist(pe)
            }
        })

        eventBus.send(om.writeValueAsString(PublishedPrice(value = prices)))

        /*
{"offer":
    {"contract":
        { "id":"82e49c2d-24e3-426b-b20d-b5691f7e44b6",
          "start":"2020-10-26T00:00:00","end":"2022-10-16T00:00:00","status":"DRAFT"},
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


private data class PublishedPrice(val event: String = "UPDATED_PRICES", val value: Map<UUID, Price>)