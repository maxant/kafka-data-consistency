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
            val e = ComponentEntity(component.id, component.parentId, contractId, config, component.componentDefinitionId, component.cardinalityKey)
            e.productId = component.productId
            e.cardinalityKey = component.cardinalityKey
            em.persist(e)
        }
    }

    fun updateConfig(contractId: UUID, componentId: UUID, param: ConfigurableParameter, newValue: String): List<ComponentEntity> {
        val components = ComponentEntity.Queries.selectByContractId(em, contractId)

        val component = components.find { it.id == componentId }
        require(component != null) { "component with id $componentId doens't appear to belong to contract $contractId" }
        val configs = om.readValue<ArrayList<Configuration<*>>>(component.configuration)
        var config = configs.find { it.name == param }
        require(config != null) { "config with name $param doens't appear to belong to component $componentId" }

        configs.remove(config)
        config = getConfiguration(config, newValue)
        configs.add(config)

        component.configuration = om.writeValueAsString(configs)

        return components
    }

}
