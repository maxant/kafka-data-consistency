package ch.maxant.kdc.mf.pricing.control

import ch.maxant.kdc.mf.library.AsyncContextAware
import ch.maxant.kdc.mf.library.Context
import ch.maxant.kdc.mf.library.MessageBuilder
import ch.maxant.kdc.mf.library.withMdcSet
import ch.maxant.kdc.mf.pricing.definitions.Price
import ch.maxant.kdc.mf.pricing.definitions.Prices
import ch.maxant.kdc.mf.pricing.dto.Configuration
import ch.maxant.kdc.mf.pricing.dto.FlatComponent
import ch.maxant.kdc.mf.pricing.dto.TreeComponent
import ch.maxant.kdc.mf.pricing.dto.Visitor
import ch.maxant.kdc.mf.pricing.entity.PriceEntity
import ch.maxant.kdc.mf.pricing.entity.PriceEntity.Queries.deleteByContractId
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.jboss.logging.Logger
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.CompletionStage
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.Observes
import javax.enterprise.event.TransactionPhase
import javax.inject.Inject
import javax.persistence.EntityManager
import kotlin.collections.HashMap

@ApplicationScoped
@SuppressWarnings("unused")
class PricingService(
        @Inject
        var em: EntityManager,

        @Inject
        var om: ObjectMapper,

        @Inject
        var context: Context,

        @Inject
        var messageBuilder: MessageBuilder
) {
    @Inject
    @Channel("event-bus-out")
    lateinit var eventBus: Emitter<String>

    @Inject
    private lateinit var pricingResultEvent: javax.enterprise.event.Event<PricingResult>

    private val log = Logger.getLogger(this.javaClass)

    @AsyncContextAware
    fun priceDraft(draft: JsonNode): CompletionStage<PricingResult> {
        // TODO add extension method to make this fetchable via a path => ur use JsonPath?
        // TODO replace with DTO
        val contract = draft.get("contract")
        val contractId = UUID.fromString(contract.get("id").asText())
        val syncTimestamp = contract.get("syncTimestamp").asLong()
        val start = LocalDateTime.parse(contract.get("start").asText())
        val end = LocalDateTime.parse(contract.get("end").asText())
        if(draft.has("pack")) {
            val pack = draft.get("pack").toString()
            val root = om.readValue(pack, TreeComponent::class.java)
            return completedFuture(priceDraft(contractId, syncTimestamp, start, end, root))
        } else if(draft.has("allComponents")) {
            val allComponents = draft.get("allComponents").toString()
            val list = om.readValue<ArrayList<FlatComponent>>(allComponents)
            return completedFuture(priceDraft(contractId, syncTimestamp, start, end, toTree(list)))
        } else {
            throw IllegalArgumentException("unexpected draft structure")
        }
    }

    fun toTree(list: List<FlatComponent>): TreeComponent {

        data class MutableTreeComponent(
                val componentId: UUID,
                val parentId: UUID?,
                val componentDefinitionId: String,
                val configs: List<Configuration>,
                val children: MutableList<MutableTreeComponent> = mutableListOf()
        )

        // map to temporary structure that contains all info (see class just above)
        val byId = list.map { MutableTreeComponent(it.id, it.parentId, it.componentDefinitionId, it.configs) }
                       .map { it.componentId to it }
                       .toMap()

        // add to kids
        byId.values.forEach { byId[it.parentId]?.children?.add(it) }

        fun map(node: MutableTreeComponent): TreeComponent {
            val children = node.children.map { map(it) }
            return TreeComponent(node.componentId.toString(), node.componentDefinitionId, node.configs, children)
        }

        return map(byId.values.find { it.parentId == null } !!)
    }

    private fun priceDraft(contractId: UUID, syncTimestamp: Long, start: LocalDateTime, end: LocalDateTime, root: TreeComponent): PricingResult {
        log.info("starting to price individual components for contract $contractId...")

        context.throwExceptionInPricingIfRequiredForDemo()

        val deletedCount = deleteByContractId(em, contractId) // start from scratch
        log.info("deleted $deletedCount existing price rows for contract $contractId")

        val prices = HashMap<UUID, Price>()
        root.accept(object: Visitor {
            override fun visit(component: TreeComponent) {
                val componentId = UUID.fromString(component.componentId)

                val rule = Prices.findRule(component)

                val price = rule(component)
                prices[componentId] = price

                val ruleName = rule.javaClass.name.substring(rule.javaClass.name.indexOf("$")+1)
                log.info("priced component ${component.componentDefinitionId}: $price using rule $ruleName")

                val pe = PriceEntity(UUID.randomUUID(), contractId, start, end,
                        componentId, ruleName, price.total, price.tax, syncTimestamp)

                em.persist(pe)
            }
        })

        val result = PricingResult(contractId, prices)

        sendEvent(result)

        return result

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

data class PricingResult(
        val contractId: UUID,
        val priceByComponentId: Map<UUID, Price>
)