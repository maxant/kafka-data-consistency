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
    fun saveInitialDraft(contractId: UUID, components: List<Component>) {
        for(component in components) {
            val config = om.writeValueAsString(component.configs)
            val e = ComponentEntity(component.id, component.parentId, contractId, config, component.componentDefinitionId)
            e.productId = component.productId
            e.cardinalityKey = component.cardinalityKey
            em.persist(e)
        }
    }

    fun updateConfig(contractId: UUID, componentId: UUID, param: ConfigurableParameter, newValue: String): List<Component> {
        val components = ComponentEntity.Queries.selectByContractId(em, contractId)

        val component = components.find { it.id == componentId }
        require(component != null) { "component with id $componentId doens't appear to belong to contract $contractId" }
        val configs = om.readValue<ArrayList<Configuration<*>>>(component.configuration)
        val config = configs.find { it.name == param }
        require(config != null) { "config with name $param doens't appear to belong to component $componentId" }

        when {
            BigDecimalConfigurationDefinition.matches(config) -> (config as BigDecimalConfiguration).value = BigDecimal(newValue)
            DateConfigurationDefinition.matches(config) -> (config as DateConfiguration).value = LocalDate.parse(newValue)
            IntConfigurationDefinition.matches(config) -> (config as IntConfiguration).value = Integer.parseInt(newValue)
            MaterialConfigurationDefinition.matches(config) -> (config as MaterialConfiguration).value = Material.valueOf(newValue)
            PercentConfigurationDefinition.matches(config) -> (config as PercentConfiguration).value = BigDecimal(newValue)
            PercentRangeConfigurationDefinition.matches(config) -> (config as PercentRangeConfiguration).value = BigDecimal(newValue)
            StringConfigurationDefinition.matches(config) -> (config as StringConfiguration).value = newValue
            else -> TODO("unexpected config defn for $config")
        }

        component.configuration = om.writeValueAsString(configs)

        return components.map { Component(om, it) }
    }

}
