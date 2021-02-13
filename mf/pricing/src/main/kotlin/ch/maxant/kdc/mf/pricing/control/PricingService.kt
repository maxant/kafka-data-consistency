package ch.maxant.kdc.mf.pricing.control

import ch.maxant.kdc.mf.library.Context
import ch.maxant.kdc.mf.pricing.definitions.Price
import ch.maxant.kdc.mf.pricing.definitions.Prices
import ch.maxant.kdc.mf.pricing.dto.Configuration
import ch.maxant.kdc.mf.pricing.dto.FlatComponent
import ch.maxant.kdc.mf.pricing.dto.TreeComponent
import ch.maxant.kdc.mf.pricing.dto.Visitor
import ch.maxant.kdc.mf.pricing.entity.PriceEntity
import ch.maxant.kdc.mf.pricing.entity.PriceEntity.Queries.deleteByContractId
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Timed
import org.eclipse.microprofile.opentracing.Traced
import org.jboss.logging.Logger
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
        @Inject
        var em: EntityManager,

        @Inject
        var om: ObjectMapper,

        @Inject
        var context: Context
) {
    private val log = Logger.getLogger(this.javaClass)

    @Transactional
    @Timed(unit = MetricUnits.MILLISECONDS)
    @Traced
    fun priceDraft(draft: JsonNode): PricingResult {
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
            return priceDraft(contractId, syncTimestamp, start, end, root)
        } else if(draft.has("allComponents")) {
            val allComponents = draft.get("allComponents").toString()
            val list = om.readValue<ArrayList<FlatComponent>>(allComponents)
            return priceDraft(contractId, syncTimestamp, start, end, toTree(list))
        } else {
            throw IllegalArgumentException("unexpected draft structure")
        }
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

    @Traced
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
