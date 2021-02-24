package ch.maxant.kdc.mf.contracts.control

import ch.maxant.kdc.mf.contracts.definitions.*
import ch.maxant.kdc.mf.contracts.dto.Component
import ch.maxant.kdc.mf.contracts.entity.ComponentEntity
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*
import javax.enterprise.context.Dependent
import javax.inject.Inject
import javax.persistence.EntityManager

@Dependent
class ComponentsRepo(
        @Inject
        var em: EntityManager,

        @Inject
        var om: ObjectMapper
){
    fun saveInitialDraft(contractId: UUID, pack: Packaging) {
        saveInitialDraft(contractId, null, pack)
    }

    private fun saveInitialDraft(contractId: UUID, parentId: UUID?, component: ComponentDefinition) {
        val config = om.writeValueAsString(component.configs)
        val e = ComponentEntity(UUID.randomUUID(), parentId, contractId, config, component.componentDefinitionId)
        if(component is Product) e.productId = component.productId
        component.componentId = e.id
        em.persist(e)
        component.children.forEach { saveInitialDraft(contractId, e.id, it) }
    }

    fun updateConfig(contractId: UUID, componentId: UUID, param: ConfigurableParameter, newValue: String): List<Component> {
        val components = ComponentEntity.Queries.selectByContractId(em, contractId)

        val component = components.find { it.id == componentId }
        require(component != null) { "component with id $componentId doens't appear to belong to contract $contractId" }
        val configs = om.readValue<ArrayList<Configuration<*>>>(component.configuration)
        val config = configs.find { it.name == param }
        require(config != null) { "config with name $param doens't appear to belong to component $componentId" }

        when {
            DateConfigurationDefinition.matches(config) -> (config as DateConfiguration).value = LocalDate.parse(newValue)
            StringConfigurationDefinition.matches(config) -> (config as StringConfiguration).value = newValue
            BigDecimalConfigurationDefinition.matches(config) -> (config as BigDecimalConfiguration).value = BigDecimal(newValue)
            IntConfigurationDefinition.matches(config) -> (config as IntConfiguration).value = Integer.parseInt(newValue)
            PercentConfigurationDefinition.matches(config) -> (config as PercentConfiguration).value = BigDecimal(newValue)
            MaterialConfigurationDefinition.matches(config) -> (config as MaterialConfiguration).value = Material.valueOf(newValue)
            else -> TODO()
        }

        val productId = components.find { it.productId != null }!!.productId!!
        val product = Products.find(productId, 1)
        val definition = getDefinition(product, component.componentDefinitionId, configs)

        definition.ensureConfigValueIsPermitted(config)
        definition.runRules(configs)

        // it's valid, so let's update the entity so that the change is persisted
        component.configuration = om.writeValueAsString(configs)

        return components.map { Component(om, it) }
    }

}
