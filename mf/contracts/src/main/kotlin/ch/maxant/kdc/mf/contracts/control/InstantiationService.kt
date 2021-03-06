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
     * correct cardinality key, based on all existing ones plus one */
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

    /** fix cardinality keys and paths of all siblings of the same time. required after removing a compnent.
     * typically the sibling is not in the tree anymore! */
    fun resetCardinalityKeysAndPaths(allComponents: List<Component>, sibling: Component): MutableList<Component> {
        val componentsOutput = mutableListOf<TreeComponent>()
        val siblings = mutableListOf<TreeComponent>()
        val rootInstance = TreeComponent(allComponents.map { ComponentObjectWrapper(it) })
        rootInstance.accept { component ->
            componentsOutput.add(component)
            if(component.component.parentId == sibling.parentId
                && component.component.componentDefinitionId == sibling.componentDefinitionId) {

                siblings.add(component)
            }
        }

        siblings
            .sortedBy { it.component.cardinalityKey }
            .withIndex()
            .forEach {
                val newCardinalityKey = it.index.plus(1).toString()
                it.value.component.cardinalityKey = newCardinalityKey
            }

        // reset path too
        return componentsOutput.map {
            val dto = it.component.original() as Component
            dto.path = it.getPath()
            dto
        }.toMutableList()
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
        return getComponentForPath(components, path).id
    }

    fun getComponentForPath(components: List<Component>, path: String): Component {
        var component: TreeComponent? = null
        TreeComponent(components.map { ComponentObjectWrapper(it) }).accept {
            if(it.getPath() == path) {
                component = it
            }
        }
        val wrapper = component?.component ?: throw IllegalArgumentException("no component found for path $path")
        return wrapper.original() as Component
    }

    private interface ComponentWrapper<T> {
        val id: UUID
        val parentId: UUID?
        val componentDefinitionId: String
        val configs: List<Configuration<*>>
        val productId: ProductId?
        var cardinalityKey: String

        fun original(): T
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
        override var cardinalityKey: String
            get() = e.cardinalityKey
            set(value) { e.cardinalityKey = value }
        override fun original() = e
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
        override var cardinalityKey: String
            get() = c.cardinalityKey
            set(value) { c.cardinalityKey = value }
        override fun original() = c
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