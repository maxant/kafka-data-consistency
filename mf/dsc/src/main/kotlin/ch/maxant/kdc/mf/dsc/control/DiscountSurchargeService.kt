package ch.maxant.kdc.mf.dsc.control

import ch.maxant.kdc.mf.dsc.definitions.DiscountsSurchargesDefinitions
import ch.maxant.kdc.mf.dsc.dto.Configuration
import ch.maxant.kdc.mf.dsc.dto.FlatComponent
import ch.maxant.kdc.mf.dsc.dto.ManualDiscountSurcharge
import ch.maxant.kdc.mf.dsc.dto.TreeComponent
import ch.maxant.kdc.mf.dsc.entity.DiscountSurchargeEntity
import ch.maxant.kdc.mf.dsc.entity.DiscountSurchargeEntity.Queries.findByContractId
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
import javax.enterprise.context.RequestScoped
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
        val draftStateForNonPersistence: DraftStateForNonPersistence
) {
    private val log = Logger.getLogger(this.javaClass)

    @Transactional
    @Timed(unit = MetricUnits.MILLISECONDS)
    @Traced
    fun handleDraft(draft: JsonNode): JsonNode {
        val persist = draft.get("persist").asBoolean()
        val contract = draft.get("contract")
        val contractId = UUID.fromString(contract.get("id").asText())
        val syncTimestamp = contract.get("syncTimestamp").asLong()
        return when {
            draft.has("pack") -> {
                TODO("delete the following code")
                /*
                val pack = draft.get("pack").toString()
                val root = om.readValue(pack, TreeComponent::class.java)
                val discountsSurcharges = handleDraft(contractId, syncTimestamp, root, persist)
                (draft as ObjectNode).set<ObjectNode>("discountsSurcharges", om.valueToTree<JsonNode>(discountsSurcharges))
                                    .put("persist", persist)
                draft
                 */
            }
            draft.has("allComponents") -> {
                val allComponents = draft.get("allComponents").toString()
                val list = om.readValue<ArrayList<FlatComponent>>(allComponents)
                val root = toTree(list)
                if(draft.has("manualDiscountsSurcharges")) {
                    val manualDiscounts = om.readValue<ArrayList<ManualDiscountSurcharge>>(draft.get("manualDiscountsSurcharges").toString())
                    manualDiscounts.forEach { handleSetDiscount(contractId, syncTimestamp, it.componentId, it.value, persist) }
                }
                val discountsSurcharges = handleDraft(contractId, syncTimestamp, root, persist)
                val pack = om.valueToTree<ObjectNode>(root)
                val draftAsTree = om.createObjectNode()
                draftAsTree
                    .set<ObjectNode>("pack", pack)
                    .set<ObjectNode>("discountsSurcharges", om.valueToTree<JsonNode>(discountsSurcharges))
                    .set<ObjectNode>("contract", contract)
                    .put("persist", persist)
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
    private fun handleDraft(contractId: UUID, syncTimestamp: Long, root: TreeComponent, persist: Boolean): Collection<DiscountSurchargeEntity> {
        log.info("calculating discounts and surcharges for contract $contractId...")

        val result = if(persist) {
            val deletedCount = DiscountSurchargeEntity.Queries.deleteByContractIdAndNotAddedManually(em, contractId) // start from scratch
            log.info("deleted $deletedCount existing discount/surcharge rows for contract $contractId that were not added manually")

            val entities = findByContractId(em, contractId)
            entities.forEach{ it.syncTimestamp = syncTimestamp} // update these guys otherwise not everything is synced
            entities
        } else {
            draftStateForNonPersistence.entities.removeIf{ !it.addedManually }
            draftStateForNonPersistence.entities
        }.toMutableList()

        val discountsSurcharges = DiscountsSurchargesDefinitions.determineDiscountsSurcharges(root)
        discountsSurcharges.forEach {
            it.contractId = contractId
            it.syncTimestamp = syncTimestamp
            if(persist) em.persist(it)
            else draftStateForNonPersistence.entities.add(it)
            result.add(it)
        }

        return result
    }

    @Transactional
    @Timed(unit = MetricUnits.MILLISECONDS)
    @Traced
    fun handleSetDiscount(cmd: JsonNode): JsonNode {
        val persist = cmd.get("persist").asBoolean()
        val contract = cmd.get("contract")
        val contractId = UUID.fromString(contract.get("id").asText())
        val componentId = UUID.fromString(cmd.get("componentId").asText())
        val value = BigDecimal(cmd.get("value").asText())
        val syncTimestamp = contract.get("syncTimestamp").asLong()
        val allComponents = cmd.get("allComponents").toString()
        val list = om.readValue<ArrayList<FlatComponent>>(allComponents)
        val root = toTree(list)
        val discountsSurcharges = handleSetDiscount(contractId, syncTimestamp, componentId, value, persist)
        val pack = om.valueToTree<ObjectNode>(root)
        val draftAsTree = om.createObjectNode()
        draftAsTree
            .set<ObjectNode>("pack", pack)
            .set<ObjectNode>("discountsSurcharges", om.valueToTree<JsonNode>(discountsSurcharges))
            .set<ObjectNode>("contract", contract)
            .put("persist", persist)
        return draftAsTree
    }

    @Traced
    private fun handleSetDiscount(contractId: UUID, syncTimestamp: Long, componentId: UUID, value: BigDecimal, persist: Boolean): List<DiscountSurchargeEntity> {
        log.info("setting discount $value on component $componentId on contract $contractId...")

        val discountsAndSurcharges = if(persist) {
                findByContractId(em, contractId)
            } else {
                draftStateForNonPersistence.entities
            }.toMutableList()

        val manuallyAddedOnComponent = discountsAndSurcharges
                                                    .filter { it.componentId == componentId }
                                                    .filter { it.addedManually }

        require(manuallyAddedOnComponent.size <= 1) {
            "unexpected number of manual discounts in contract $contractId on component $componentId: ${manuallyAddedOnComponent.size}"
        }

        if(manuallyAddedOnComponent.isEmpty()) {
            val e = DiscountSurchargeEntity(UUID.randomUUID(), contractId, componentId, "MANUAL", value, syncTimestamp, true)
            if(persist) em.persist(e)
            else draftStateForNonPersistence.entities.add(e)
            discountsAndSurcharges.add(e)
        } else {
            manuallyAddedOnComponent[0].value = value
        }

        // update all existing to be in sync with contract
        discountsAndSurcharges.forEach { it.syncTimestamp = syncTimestamp }

        return discountsAndSurcharges
    }
}

@RequestScoped
class DraftStateForNonPersistence {
    var entities = mutableListOf<DiscountSurchargeEntity>()
}