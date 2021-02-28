package ch.maxant.kdc.mf.dsc.control

import ch.maxant.kdc.mf.dsc.definitions.DiscountsSurchargesDefinitions
import ch.maxant.kdc.mf.dsc.dto.Configuration
import ch.maxant.kdc.mf.dsc.dto.FlatComponent
import ch.maxant.kdc.mf.dsc.dto.TreeComponent
import ch.maxant.kdc.mf.dsc.entity.DiscountSurchargeEntity
import ch.maxant.kdc.mf.dsc.entity.DiscountSurchargeEntity.Queries.findByContractId
import ch.maxant.kdc.mf.library.Context
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.readValue
import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Timed
import org.eclipse.microprofile.opentracing.Traced
import org.jboss.logging.Logger
import java.math.BigDecimal
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.persistence.EntityManager
import javax.transaction.Transactional

@ApplicationScoped
@SuppressWarnings("unused")
class DiscountSurchargeService(
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
    fun handleDraft(draft: JsonNode): JsonNode {
        val contract = draft.get("contract")
        val contractId = UUID.fromString(contract.get("id").asText())
        val syncTimestamp = contract.get("syncTimestamp").asLong()
        return when {
            draft.has("pack") -> {
                val pack = draft.get("pack").toString()
                val root = om.readValue(pack, TreeComponent::class.java)
                val discountsSurcharges = handleDraft(contractId, syncTimestamp, root)
                (draft as ObjectNode).set<ObjectNode>("discountsSurcharges", om.valueToTree<JsonNode>(discountsSurcharges))
                draft
            }
            draft.has("allComponents") -> {
                val allComponents = draft.get("allComponents").toString()
                val list = om.readValue<ArrayList<FlatComponent>>(allComponents)
                val root = toTree(list)
                val discountsSurcharges = handleDraft(contractId, syncTimestamp, root)
                val pack = om.valueToTree<ObjectNode>(root)
                val draftAsTree = om.createObjectNode()
                draftAsTree
                    .set<ObjectNode>("pack", pack)
                    .set<ObjectNode>("discountsSurcharges", om.valueToTree<JsonNode>(discountsSurcharges))
                    .set<ObjectNode>("contract", contract)
                draftAsTree
            }
            else -> {
                throw IllegalArgumentException("unexpected draft structure")
            }
        }
    }

    fun toTree(list: List<FlatComponent>): TreeComponent {

        data class MutableTreeComponent(
                val componentId: UUID,
                val parentId: UUID?,
                val componentDefinitionId: String,
                val configs: List<Configuration>,
                val children: MutableList<MutableTreeComponent> = mutableListOf(),
                val productId: String?
        )

        // map to temporary structure that contains all info (see class just above)
        val byId = list.map { MutableTreeComponent(it.id, it.parentId, it.componentDefinitionId, it.configs, productId = it.productId) }
                       .map { it.componentId to it }
                       .toMap()

        // add to kids
        byId.values.forEach { byId[it.parentId]?.children?.add(it) }

        fun map(node: MutableTreeComponent): TreeComponent {
            val children = node.children.map { map(it) }
            return TreeComponent(node.componentId.toString(), node.componentDefinitionId, node.configs, children, node.productId)
        }

        return map(byId.values.find { it.parentId == null } !!)
    }

    @Traced
    private fun handleDraft(contractId: UUID, syncTimestamp: Long, root: TreeComponent): Collection<DiscountSurchargeEntity> {
        log.info("calculating discounts and surcharges for contract $contractId...")

        val deletedCount = DiscountSurchargeEntity.Queries.deleteByContractIdAndNotAddedManually(em, contractId) // start from scratch
        log.info("deleted $deletedCount existing discount/surcharge rows for contract $contractId that were not added manually")

        var discountsSurcharges = DiscountsSurchargesDefinitions.determineDiscountsSurcharges(root)
        discountsSurcharges.forEach {
            it.contractId = contractId
            it.syncTimestamp = syncTimestamp
            em.persist(it)
        }

        return findByContractId(em, contractId) // we need to fetch the ones added manually too
    }

    @Transactional
    @Timed(unit = MetricUnits.MILLISECONDS)
    @Traced
    fun handleSetDiscount(cmd: JsonNode): JsonNode {
        val contract = cmd.get("contract")
        val contractId = UUID.fromString(contract.get("id").asText())
        val componentId = UUID.fromString(cmd.get("componentId").asText())
        val value = BigDecimal(cmd.get("value").asText())
        val syncTimestamp = contract.get("syncTimestamp").asLong()
        val allComponents = cmd.get("allComponents").toString()
        val list = om.readValue<ArrayList<FlatComponent>>(allComponents)
        val root = toTree(list)
        val discountsSurcharges = handleSetDiscount(contractId, syncTimestamp, componentId, value)
        val pack = om.valueToTree<ObjectNode>(root)
        val draftAsTree = om.createObjectNode()
        draftAsTree
            .set<ObjectNode>("pack", pack)
            .set<ObjectNode>("discountsSurcharges", om.valueToTree<JsonNode>(discountsSurcharges))
            .set<ObjectNode>("contract", contract)
        return draftAsTree
    }

    @Traced
    private fun handleSetDiscount(contractId: UUID, syncTimestamp: Long, componentId: UUID, value: BigDecimal): List<DiscountSurchargeEntity> {
        log.info("setting discount $value on component $componentId on contract $contractId...")

        val discountsAndSurcharges = findByContractId(em, contractId).toMutableList()

        val manuallyAddedOnComponent = discountsAndSurcharges
                                                    .filter { it.componentId == componentId }
                                                    .filter { it.addedManually }

        require(manuallyAddedOnComponent.size <= 1) {
            "unexpected number of manual discounts in contract $contractId on component $componentId: ${manuallyAddedOnComponent.size}"
        }

        if(manuallyAddedOnComponent.isEmpty()) {
            val e = DiscountSurchargeEntity(UUID.randomUUID(), contractId, componentId, "MANUAL", value, syncTimestamp, true)
            em.persist(e)
            discountsAndSurcharges.add(e)
        } else {
            manuallyAddedOnComponent[0].value = value
        }

        // update all existing to be in sync with contract
        discountsAndSurcharges.forEach { it.syncTimestamp = syncTimestamp }

        return discountsAndSurcharges
    }
}
