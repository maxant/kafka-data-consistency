package ch.maxant.kdc.mf.contracts.control

import ch.maxant.kdc.mf.contracts.definitions.Configuration
import ch.maxant.kdc.mf.contracts.definitions.ProductId
import ch.maxant.kdc.mf.contracts.dto.Component
import ch.maxant.kdc.mf.contracts.entity.ComponentEntity
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject

@ApplicationScoped
@SuppressWarnings("unused")
class InstantiationService(
    @Inject var om: ObjectMapper
) {

    fun instantiate(mergedComponentDefinition: MergedComponentDefinition, parentId: UUID? = null): List<Component> {
        val componentsOutput: MutableList<Component> = mutableListOf()
        instantiate(mergedComponentDefinition, componentsOutput, parentId)
        return componentsOutput
    }

    private fun instantiate(mergedComponentDefinition: MergedComponentDefinition,
                             componentsOutput: MutableList<Component>, parentId: UUID? = null, path: String? = null): List<Component> {

        // instantiate the definition the required number of times
        1.rangeTo(mergedComponentDefinition.cardinalityDefault).forEach { cardinalityKey ->
            var newPath = "${mergedComponentDefinition.componentDefinitionId}$cardinalityKey"
            if(path != null) newPath = "$path->$newPath"
            val component = Component(
                UUID.randomUUID(),
                parentId,
                mergedComponentDefinition.componentDefinitionId,
                mergedComponentDefinition.configs,
                mergedComponentDefinition.productId,
                "$cardinalityKey",
                newPath
            )

            componentsOutput.add(component)

            mergedComponentDefinition.children.forEach {
                instantiate(it, componentsOutput, component.id, newPath)
            }
        }
        return componentsOutput
    }

    /** instantiate the given mergedComponentDefinition into the instance tree defined by allComponents, with the parent
     * having the supplied ID. returns a list of the new component and all its children. this algorithm calculates the
     * correct cardinality key, based on all existing ones plus one, so if you remove and re-add subtrees you can end
     * up with higher numbers than the max cardinality! */
    fun instantiateSubtree(allComponents: List<Component>, mergedComponentDefinition: MergedComponentDefinition, pathToAdd: String): List<Component> {
        val parentId: UUID = getComponentIdForPath(allComponents, pathToAdd.substring(0, pathToAdd.lastIndexOf("->")))
        val componentsOutput = mutableListOf<Component>()
        val rootInstance = TreeComponent(allComponents.map { ComponentObjectWrapper(it) })
        rootInstance.accept { parent ->
            if(parent.component.id == parentId) {
                val cardinalityKey = (parent.children
                                       .filter { it.component.componentDefinitionId == mergedComponentDefinition.componentDefinitionId }
                                       .map { it.component.cardinalityKey.toInt() }
                                       .max() ?: 0) + 1
                val newPath = "${parent.getPath()}->${mergedComponentDefinition.componentDefinitionId}$cardinalityKey"
                val component = Component(
                    UUID.randomUUID(),
                    parentId,
                    mergedComponentDefinition.componentDefinitionId,
                    mergedComponentDefinition.configs,
                    mergedComponentDefinition.productId,
                    "$cardinalityKey",
                    newPath
                )

                componentsOutput.add(component)

                mergedComponentDefinition.children.forEach {
                    instantiate(it, componentsOutput, component.id, newPath)
                }
            }
        }
        return componentsOutput
    }

    /** builds an internal tree of allComponents and then validates each node in the tree against
     * the matching node from the merged definition, based on paths. the configs in the
     * merged component definitions are irrelevant (with initial/default values), because we use the actual
     * configs from allComponents. */
    fun validate(mergedComponentDefinition: MergedComponentDefinition, allComponents: List<Component>) {

        // create a temporary tree of the flat components, so we can retrieve and compare subtrees
        val rootInstance = TreeComponent(allComponents.map { ComponentObjectWrapper(it) })

        // now apply values out of components onto mergedComponentDefinition
        rootInstance.accept {
            val definitionSubtree = mergedComponentDefinition.find(it.getPath())!!
            validate(definitionSubtree, it)
        }
    }

    private fun validate(mergedComponentDefinition: MergedComponentDefinition, treeComponent: TreeComponent) {
        treeComponent.component.configs.forEach {
            try {
                mergedComponentDefinition.ensureConfigValueIsPermitted(it)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("${e.message} at path ${treeComponent.getPath()}")
            }
        }

        try {
            mergedComponentDefinition.runRules(treeComponent.component.configs, treeComponent.component)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("${e.message} at path ${treeComponent.getPath()}")
        }

        if(treeComponent.parent != null) {
            val numOfSiblings = treeComponent.parent.children
                .filter { it.getPath().matches(Regex(mergedComponentDefinition.getPath())) }
                .count()
            require(numOfSiblings >= mergedComponentDefinition.cardinalityMin) { "too few  kids of type ${treeComponent.component.componentDefinitionId} in path ${treeComponent.parent.getPath()}" }
            require(numOfSiblings <= mergedComponentDefinition.cardinalityMax) { "too many kids of type ${treeComponent.component.componentDefinitionId} in path ${treeComponent.parent.getPath()}" }
        }
    }

    fun reinstantiate(entities: List<ComponentEntity>): List<Component> {
        return TreeComponent(entities.map { ComponentEntityWrapper(it, om) })
            .flatten()
            .map {
                Component(
                    it.component.id,
                    it.component.parentId,
                    it.component.componentDefinitionId,
                    it.component.configs,
                    it.component.productId,
                    it.component.cardinalityKey,
                    it.getPath()
                )
            }
    }

    fun getComponentIdForPath(components: List<Component>, path: String): UUID {
        var id: UUID? = null
        TreeComponent(components.map { ComponentObjectWrapper(it) }).accept {
            if(it.getPath() == path) {
                id = it.component.id
            }
        }
        return id ?: throw IllegalArgumentException("no component found for path $path")
    }

    private interface ComponentWrapper<T> {
        val id: UUID
        val parentId: UUID?
        val componentDefinitionId: String
        val configs: List<Configuration<*>>
        val productId: ProductId?
        val cardinalityKey: String
    }

    private class ComponentEntityWrapper(private val e: ComponentEntity, private val om: ObjectMapper) : ComponentWrapper<ComponentEntity> {
        override val id: UUID
            get() = e.id
        override val parentId: UUID?
            get() = e.parentId
        override val componentDefinitionId: String
            get() = e.componentDefinitionId
        override val configs: List<Configuration<*>>
            get() = om.readValue<ArrayList<Configuration<*>>>(e.configuration)
        override val productId: ProductId?
            get() = e.productId
        override val cardinalityKey: String
            get() = e.cardinalityKey
    }

    private class ComponentObjectWrapper(private val c: Component) : ComponentWrapper<Component> {
        override val id: UUID
            get() = c.id
        override val parentId: UUID?
            get() = c.parentId
        override val componentDefinitionId: String
            get() = c.componentDefinitionId
        override val configs: List<Configuration<*>>
            get() = c.configs
        override val productId: ProductId?
            get() = c.productId
        override val cardinalityKey: String
            get() = c.cardinalityKey
    }

    private class TreeComponent(allComponents: List<ComponentWrapper<*>>,
                                val component: ComponentWrapper<*> = allComponents.find { it.parentId == null }!!,
                                val parent: TreeComponent? = null) {

        val children = mutableListOf<TreeComponent>()

        init {
            parent?.children?.add(this)

            allComponents.filter { it.parentId == component.id }.forEach {
                TreeComponent(allComponents, it, this)
            }
        }

        fun getPath(): String {
            val name = component.componentDefinitionId + component.cardinalityKey
            return if(parent == null) name else "${parent.getPath()}->$name"
        }

        /** children last recursion aka pre order traversal
         * https://towardsdatascience.com/4-types-of-tree-traversal-algorithms-d56328450846 */
        fun accept(f: (treeComponent: TreeComponent) -> Unit) {
            f(this)
            children.forEach { it.accept(f) }
        }

        fun flatten(): List<TreeComponent> {
            val list = mutableListOf<TreeComponent>()
            this.accept { list.add(it) }
            return list
        }
    }

}