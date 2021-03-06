package ch.maxant.kdc.mf.pricing.control

import com.fasterxml.jackson.module.kotlin.readValue
import ch.maxant.kdc.mf.library.Context
import ch.maxant.kdc.mf.pricing.definitions.Price
import ch.maxant.kdc.mf.pricing.definitions.Prices
import ch.maxant.kdc.mf.pricing.definitions.roundAddTaxAndMakePrice
import ch.maxant.kdc.mf.pricing.dto.TreeComponent
import ch.maxant.kdc.mf.pricing.dto.Visitor
import ch.maxant.kdc.mf.pricing.entity.PriceEntity
import ch.maxant.kdc.mf.pricing.entity.PriceEntity.Queries.deleteByContractId
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.quarkus.redis.client.RedisClient
import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Timed
import org.eclipse.microprofile.opentracing.Traced
import org.jboss.logging.Logger
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.persistence.EntityManager
import javax.transaction.Transactional
import kotlin.collections.HashMap

@ApplicationScoped
@SuppressWarnings("unused")
class PricingService(
        @Inject val em: EntityManager,
        @Inject val om: ObjectMapper,
        @Inject val context: Context,
        @Inject val redis: RedisClient
) {
    private val log = Logger.getLogger(this.javaClass)

    @Transactional
    @Timed(unit = MetricUnits.MILLISECONDS)
    @Traced
    fun priceDraft(draft: JsonNode): PricingResult {
        // TODO add extension method to make this fetchable via a path => ur use JsonPath?
        // TODO replace with DTO
        val persist = PersistenceTypes.valueOf(draft.get("persist").asText())
        val contract = draft.get("contract")
        val contractId = UUID.fromString(contract.get("id").asText())
        val syncTimestamp = contract.get("syncTimestamp").asLong()
        val start = LocalDateTime.parse(contract.get("start").asText())
        val end = LocalDateTime.parse(contract.get("end").asText())
        val pack = draft.get("pack").toString()
        val discountsSurcharges = om.readValue<List<DiscountSurcharge>>(draft.get("discountsSurcharges").toString())
        val root = om.readValue(pack, TreeComponent::class.java)
        return priceDraft(contractId, syncTimestamp, start, end, root, discountsSurcharges, persist)
    }

    @Transactional
    @Timed(unit = MetricUnits.MILLISECONDS)
    @Traced
    fun readPrices(group: PricingCommandGroup): PricingCommandGroupResult {
        if(group.failForTestingPurposes) {
            log.error("FAILING FOR TEST PURPOSES")
            throw RuntimeException("FAILING FOR TEST PURPOSES")
        }

        val contractIds = group.commands.map { it.contractId }.distinct()
        val entitiesOrderedByStart = PriceEntity.Queries.selectByContractIdsOrderedByStart(em, contractIds)
        val results = mutableListOf<PricingCommandResult>()
        for(contractId in contractIds) {
            val entitiesForContractOrderedByStart = entitiesOrderedByStart.filter { it.contractId == contractId }
            val commandsForContract = group.commands.filter { it.contractId == contractId }
            require(commandsForContract.size == 1) { "Request to read price of contract for more than one period is not currently supported! $commandsForContract" }
            val commandForContract = commandsForContract[0]

            val componentIds = entitiesForContractOrderedByStart.map { it.componentId }.distinct()
            val componentPrices = mutableMapOf<UUID, ComponentPriceWithValidity>()
            for(componentId in componentIds) {
                val entity = entitiesForContractOrderedByStart.filter { it.componentId == componentId }
                    .find { it.start.toLocalDate() <= commandsForContract[0].from
                            && it.end.toLocalDate() >= commandsForContract[0].to }

                require(entity != null) { "no matching price found for $commandForContract and component $componentId"}
                val price = Price(entity.price, entity.tax)
                val priceWithValidity = ComponentPriceWithValidity(entity.componentId, price,
                                                                    entity.start.toLocalDate(), entity.end.toLocalDate())
                componentPrices[componentId] = priceWithValidity
            }
            results.add(PricingCommandResult(contractId, componentPrices))
        }
        return PricingCommandGroupResult(group.groupId, results, false)
    }

    @Transactional
    @Timed(unit = MetricUnits.MILLISECONDS)
    @Traced
    fun repriceContract(group: PricingCommandGroup): PricingCommandGroupResult {
        if(group.failForTestingPurposes) {
            log.error("FAILING FOR TEST PURPOSES")
            throw RuntimeException("FAILING FOR TEST PURPOSES")
        }

        val contractIds = group.commands.map { it.contractId }.distinct()
        val entitiesOrderedByStart = PriceEntity.Queries.selectByContractIdsOrderedByStart(em, contractIds)
        for(contractId in contractIds) {
            val entitiesForContractOrderedByStart = entitiesOrderedByStart.filter { it.contractId == contractId }
            val commandsForContract = group.commands.filter { it.contractId == contractId }

            // case 1 - more than one entry, command is to split based on the last entry
            // |------------------|------------------|
            //                              |-- date from which a new price is needed => split at this date
            //
            // case 2 - just one entry
            // |------------------|
            //        |-- date from which a new price is needed => reduces to case 1
            //
            // case 3 - anything else => error, unexpected
            val componentIds = entitiesForContractOrderedByStart.map { it.componentId }.distinct()
            for(componentId in componentIds) {
                val lastEntityForComponentAndContract = entitiesForContractOrderedByStart.filter { it.componentId == componentId }.last()

                if(commandsForContract.size == 1) {
                    // just ensure that a price exists for the given period
                    TODO()
                } else if(commandsForContract.size == 2) {
                    TODO()
                } else throw IllegalStateException("that should never happen")
            }
        }
        return PricingCommandGroupResult(group.groupId, TODO(), true)
    }

    @Traced
    private fun priceDraft(contractId: UUID, syncTimestamp: Long, start: LocalDateTime, end: LocalDateTime,
                           root: TreeComponent, discountsSurcharges: List<DiscountSurcharge>, persist: PersistenceTypes): PricingResult {
        log.info("starting to price individual components for contract $contractId...")

        context.throwExceptionInPricingIfRequiredForDemo()

        when(persist) {
            PersistenceTypes.DB -> {
                val deletedCount = deleteByContractId(em, contractId) // start from scratch
                log.info("deleted $deletedCount existing price rows for contract $contractId")
            }
            PersistenceTypes.IN_MEMORY -> Unit
            PersistenceTypes.REDIS -> Unit // no deletion, as we would just replace the entire document
        }

        val discountsSurchargesByComponentId = discountsSurcharges.groupBy { it.componentId }
        val prices = HashMap<UUID, Price>()
        root.accept(object: Visitor {
            override fun visit(component: TreeComponent) {
                val componentId = UUID.fromString(component.componentId)

                val rule = Prices.findRule(component)

                var price = rule(component, prices)

                val ruleName = rule.javaClass.name.substring(rule.javaClass.name.indexOf("$")+1)
                log.info("priced component ${component.componentDefinitionId}: $price using rule $ruleName")

                val discountsSurchargesForComponent = discountsSurchargesByComponentId[componentId] ?: emptyList()
                if(discountsSurchargesForComponent.isNotEmpty()) {
                    log.info("applying discounts/surcharges ${discountsSurchargesForComponent.map { it.definitionId }} to component " +
                            "${component.componentDefinitionId} with values: ${discountsSurchargesForComponent.map { it.value }}")

                    val multiplicand = discountsSurchargesForComponent.map { it.value }.reduce { acc, it -> acc.add(it) }.add(BigDecimal.ONE)
                    price = roundAddTaxAndMakePrice(price.total.subtract(price.tax).multiply(multiplicand))
                    log.info("new price is $price")
                }

                prices[componentId] = price

                when(persist) {
                    PersistenceTypes.DB -> {
                        val pe = PriceEntity(UUID.randomUUID(), contractId, start, end,
                                componentId, ruleName, price.total, price.tax, syncTimestamp)
                        em.persist(pe)
                    }
                    PersistenceTypes.IN_MEMORY -> Unit
                    PersistenceTypes.REDIS -> redis.set(listOf("$contractId-pricing", syncTimestamp).map {it.toString()})
                }
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

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// input from DSC
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
data class DiscountSurcharge(
    val componentId: UUID,
    val definitionId: String,
    val value: BigDecimal
)

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// pricing result for drafts and updates to drafts
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
data class PricingResult(
        val contractId: UUID,
        val priceByComponentId: Map<UUID, Price>
)

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// pricing commands for e.g. billing (as well as result as an event) - this is OUR interface, we define the contract (based on consumers requirements)
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
data class PricingCommandGroup(val groupId: UUID, val commands: List<PricingCommand>, val recalculate: Boolean, val failForTestingPurposes: Boolean)

data class PricingCommand(val contractId: UUID, val from: LocalDate, val to: LocalDate)

data class PricingCommandGroupResult(val groupId: UUID,
                                     val commands: List<PricingCommandResult>,
                                     @field:JsonIgnore val recalculated: Boolean,
                                     val failed: Boolean = false,
                                     var failedReason: String? = null)

data class PricingCommandResult(val contractId: UUID,
                                val priceByComponentId: Map<UUID, ComponentPriceWithValidity> = emptyMap())

data class ComponentPriceWithValidity(val componentId: UUID, val price: Price, val from: LocalDate, val to: LocalDate)


enum class PersistenceTypes {
    IN_MEMORY, REDIS, DB
}