package ch.maxant.kdc.mf.contracts.control

import ch.maxant.kdc.mf.contracts.definitions.*
import ch.maxant.kdc.mf.contracts.dto.Component
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject

@ApplicationScoped
@SuppressWarnings("unused")
class InstantiationService(
    @Inject val definitionService: DefinitionService
) {

    fun instantiate(mergedComponentDefinition: MergedComponentDefinition): List<Component> {
        val componentsOutput: MutableList<Component> = mutableListOf()
        _instantiate(mergedComponentDefinition, componentsOutput)
        return componentsOutput
    }

    private fun _instantiate(mergedComponentDefinition: MergedComponentDefinition,
                             componentsOutput: MutableList<Component>, parent: Component? = null, path: String? = null): List<Component> {

        // instantiate the definition the minimum amount of times required
        1.rangeTo(mergedComponentDefinition.cardinalityMin).forEach { cardinalityKey ->
            val cardKey = if(mergedComponentDefinition.cardinalityMin > 1) "$cardinalityKey" else ""
            var newPath = "${mergedComponentDefinition.componentDefinitionId}$cardKey"
            if(path != null) newPath = "$path->$newPath"
            val component = Component(
                UUID.randomUUID(),
                parent?.id,
                mergedComponentDefinition.componentDefinitionId,
                mergedComponentDefinition.configs,
                mergedComponentDefinition.productId,
                "$cardKey")
            component.path = newPath

            componentsOutput.add(component)

            mergedComponentDefinition.children.forEach {
                _instantiate(it, componentsOutput, component, newPath)
            }
        }
        return componentsOutput
    }

    /** builds an internal tree of allComponents and then validates each node in the tree against
     * the matching node from the merged definition, based on paths. the configs in the
     * merged component definitions are irrelevant, because we use the actual configs from allComponents */
    fun validate(mergedComponentDefinition: MergedComponentDefinition, allComponents: List<Component>) {

        // create a temporary tree of the flat components, so we can retrieve and compare subtrees
        val rootInstance = TreeComponent(allComponents.find { it.parentId == null }!!, allComponents)

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

    fun getPath(component: Component, allComponents: List<Component>): String {
        val root = TreeComponent(allComponents.find { it.parentId == null }!!, allComponents)
        var path: String? = null
        root.accept { if(it.component == component) path = it.getPath() }
        return path!!
    }

    private class TreeComponent(val component: Component, allComponents: List<Component>, val parent: TreeComponent? = null) {

        val children = mutableListOf<TreeComponent>()

        init {
            parent?.children?.add(this)

            allComponents.filter { it.parentId == component.id }.forEach {
                TreeComponent(it, allComponents, this)
            }
        }

        fun getPath(): String {
            val name = component.componentDefinitionId
            val cardKey = component.cardinalityKey ?: ""
            return if(parent == null) name else "${parent.getPath()}->$name$cardKey"
        }

        /** children last recursion aka pre order traversal
         * https://towardsdatascience.com/4-types-of-tree-traversal-algorithms-d56328450846 */
        fun accept(f: (treeComponent: TreeComponent) -> Unit) {
            f(this)
            children.forEach { it.accept(f) }
        }
    }

}