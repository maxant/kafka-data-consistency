package ch.maxant.kdc.mf.contracts.control

import ch.maxant.kdc.mf.contracts.definitions.ComponentDefinition
import ch.maxant.kdc.mf.contracts.definitions.Packaging
import ch.maxant.kdc.mf.contracts.definitions.Product
import ch.maxant.kdc.mf.contracts.entity.ComponentEntity
import com.fasterxml.jackson.databind.ObjectMapper
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
    fun saveInitialOffer(contractId: UUID, pack: Packaging) {
        saveInitialOffer(contractId, null, pack)
    }

    private fun saveInitialOffer(contractId: UUID, parentId: UUID?, component: ComponentDefinition) {
        val config = om.writeValueAsString(component.configs)
        val e = ComponentEntity(UUID.randomUUID(), parentId, contractId, config.toString(), component.componentDefinitionId)
        if(component is Product) e.productId = component.productId
        component.componentId = e.id
        em.persist(e)
        component.children.forEach { saveInitialOffer(contractId, e.id, it) }
    }
}
