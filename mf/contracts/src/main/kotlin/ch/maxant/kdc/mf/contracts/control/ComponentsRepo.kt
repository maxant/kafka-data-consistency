package ch.maxant.kdc.mf.contracts.control

import ch.maxant.kdc.mf.contracts.boundary.DraftStateForNonPersistence
import ch.maxant.kdc.mf.contracts.boundary.PersistenceTypes
import ch.maxant.kdc.mf.contracts.definitions.*
import ch.maxant.kdc.mf.contracts.dto.Component
import ch.maxant.kdc.mf.contracts.entity.ComponentEntity
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.quarkus.redis.client.RedisClient
import java.util.*
import javax.enterprise.context.Dependent
import javax.inject.Inject
import javax.persistence.EntityManager

@Dependent
class ComponentsRepo(
        @Inject val em: EntityManager,
        @Inject val om: ObjectMapper,
        @Inject val instantiationService: InstantiationService,
        @Inject val draftStateForNonPersistence: DraftStateForNonPersistence,
        @Inject val redis: RedisClient
){
    fun saveInitialDraft(contractId: UUID, components: List<Component>) {
        addComponents(contractId, components)
    }

    /** hides implementation details about how configurations are persisted */
    fun updateConfig(components: List<ComponentEntity>, componentId: UUID, param: ConfigurableParameter, newValue: String): List<Component> {
        val component = components.find { it.id == componentId }
        require(component != null) { "component with id $componentId not present. contract is: ${components.map { it.contractId }}" }
        val configs = om.readValue<ArrayList<Configuration<*>>>(component.configuration)
        var config = configs.find { it.name == param }
        require(config != null) { "config with name $param not found in component $componentId" }

        configs.remove(config)
        config = getConfiguration(config, newValue)
        configs.add(config)

        component.configuration = om.writeValueAsString(configs)

        if(draftStateForNonPersistence.persist == PersistenceTypes.REDIS) {
            redis.set(listOf("${components[0].contractId}-components", om.writeValueAsString(components)))
        }
        // else if in-memory - they were taken from the store, so are now up to date
        // else if db - the objects are attached and so will be updated

        return instantiationService.reinstantiate(components)
    }

    fun addComponents(contractId: UUID, components: List<Component>, originalComponents: MutableList<Component> = mutableListOf()) {
        for(component in components) {
            val e = toComponentEntity(component, contractId)
            if(draftStateForNonPersistence.persist == PersistenceTypes.DB) {
                em.persist(e)
            } else if(draftStateForNonPersistence.persist == PersistenceTypes.IN_MEMORY) {
                draftStateForNonPersistence.addComponent(e)
            }
        }

        if(draftStateForNonPersistence.persist == PersistenceTypes.REDIS) {
            val list = originalComponents.toMutableList()
            list.addAll(components)
            redis.set(listOf("$contractId-components", om.writeValueAsString(list.map { toComponentEntity(it, contractId) })))
        }
    }

    private fun toComponentEntity(component: Component, contractId: UUID): ComponentEntity {
        val config = om.writeValueAsString(component.configs)
        val e = ComponentEntity(component.id, component.parentId, contractId, config, component.componentDefinitionId,
            component.cardinalityKey
        )
        e.productId = component.productId
        e.cardinalityKey = component.cardinalityKey
        return e
    }

}
