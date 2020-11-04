package ch.maxant.kdc.mf.pricing.control

import ch.maxant.kdc.mf.library.AsyncContextAware
import ch.maxant.kdc.mf.pricing.definitions.Price
import ch.maxant.kdc.mf.pricing.definitions.Prices
import ch.maxant.kdc.mf.pricing.dto.Component
import ch.maxant.kdc.mf.pricing.dto.Visitor
import ch.maxant.kdc.mf.pricing.entity.PriceEntity
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.jboss.logging.Logger
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.CompletionStage
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.persistence.EntityManager
import kotlin.collections.HashMap

@ApplicationScoped
@SuppressWarnings("unused")
class PricingService(
        @Inject
        var em: EntityManager,

        @Inject
        var om: ObjectMapper
) {
    private val log = Logger.getLogger(this.javaClass)

    @AsyncContextAware
    fun priceDraft(event: JsonNode): CompletionStage<PricingResult> {
        // TODO add extension method to make this fetchable via a path => ur use JsonPath?
        // TODO replace with DTO
        val value = event.get("value")
        val contractId = UUID.fromString(value.get("contract").get("id").asText())
        val pack = value.get("pack").toString()
        val start = LocalDateTime.parse(value.get("contract").get("start").asText())
        val end = LocalDateTime.parse(value.get("contract").get("end").asText())
        val root = om.readValue(pack, Component::class.java)

        return completedFuture(price(contractId, start, end, root))
    }

    private fun price(contractId: UUID, start: LocalDateTime, end: LocalDateTime, root: Component): PricingResult {
        log.info("starting to price individual components...")
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
        return PricingResult(contractId, prices)

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

data class PricingResult(
        val contractId: UUID,
        val prices: Map<UUID, Price>
)