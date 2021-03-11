package ch.maxant.kdc.mf.contracts.control

import ch.maxant.kdc.mf.contracts.definitions.*
import ch.maxant.kdc.mf.contracts.dto.Component
import java.util.*
import javax.enterprise.context.ApplicationScoped

@ApplicationScoped
@SuppressWarnings("unused")
class InstantiationService {

    fun instantiate(pack: Packaging, marketingDefinitions: MarketingDefinitions): List<Component> {
        val components: MutableList<Component> = mutableListOf()
        _instantiate(pack, marketingDefinitions, pack.javaClass.simpleName, components, null)
        validate(listOf(pack), marketingDefinitions, components)
        return components
    }

    private fun _instantiate(componentDefinition: ComponentDefinition,
                             marketingDefinitions: MarketingDefinitions, path: String,
                             components: MutableList<Component>, parent: Component?): List<Component> {

        val defaults = marketingDefinitions.getComponent(path)
        val configsToUse = mergeConfigs(componentDefinition, defaults)

        val productId = if(componentDefinition is Product) componentDefinition.productId else null
        val cardinalityMin = defaults?.cardinalityMin ?: componentDefinition.cardinalityMin

        // instantiate the definition the minimum amount of times required
        1.rangeTo(cardinalityMin).forEach { cardinalityKey ->
            val component = Component(
                UUID.randomUUID(),
                parent?.id, componentDefinition.componentDefinitionId, configsToUse, productId, "$cardinalityKey")

            components.add(component)

            val cardKey = if(cardinalityMin > 1) "$::cardinalityKey" else ""
            componentDefinition.children.forEach {
                _instantiate(it, marketingDefinitions, "$path->$componentDefinition$cardKey", components, component)
            }
        }
        return components
    }

    private fun mergeConfigs(componentDefinition: ComponentDefinition, defaultComponent: DefaultComponent?) =
        componentDefinition.configs.map { config ->
            val defaultConfig =
                defaultComponent?.configs?.find { defConfig -> defConfig.name == config.name } ?: null
            if (defaultConfig != null) {
                getConfiguration(config, defaultConfig.value)
            } else {
                config
            }
        }

    fun validate(rootDefinitions: List<ComponentDefinition>, marketingDefinitions: MarketingDefinitions, components: List<Component>) {
        val instanceRoot = Node.buildTree(components)
        instanceRoot.accept { _validate(it, rootDefinitions, marketingDefinitions) }
    }

    private fun _validate(node: Node, rootDefinitions: List<ComponentDefinition>, marketingDefinitions: MarketingDefinitions) {
        val marketingDefinition = marketingDefinitions.getComponent(node.getPath())
        val componentDefinition = getDefinition(rootDefinitions, node.component.componentDefinitionId, node.component.configs)
        val configsDefinitionsToUse = mergeConfigs(componentDefinition, marketingDefinition)

// TODO        marketing defns also override possible config values;

        node.component.configs.forEach { componentDefinition.ensureConfigValueIsPermitted(it) }
        componentDefinition.runRules(node.component.configs)
        val numOfSiblingsWithSameComponentDefinitionId = node.parent?.children?.filter { it.component.componentDefinitionId == node.component.componentDefinitionId }?.count() ?: 0
        require(componentDefinition.cardinalityMin > numOfSiblingsWithSameComponentDefinitionId ) { "too few kids of type ${node.component.componentDefinitionId} in path ${node.getPath()}" }
        require(componentDefinition.cardinalityMax < numOfSiblingsWithSameComponentDefinitionId ) { "too many kids of type ${node.component.componentDefinitionId} in path ${node.getPath()}" }
/*
        // 1. configs must be in the range of values allowed by the component definition
        componentDefinition.ensureConfigValueIsPermitted(node.component.configs)

        // 2. rules must evaluate to true
        componentDefinition.runRules(node.component.configs)

        // 3.
//TODO;
        configsDefinitionsToUse.forEach { configDefinitionToUse ->
            val actualConfig = node.component.configs.find { it.name == configDefinitionToUse.name }!!
            componentDefinition.ensureConfigValueIsPermitted(actualConfig)
        }
*/
    }

    /** allows us to navigate in both directions */
    private class Node(val component: Component, val parent: Node? = null) {
        val children: MutableList<Node> = mutableListOf()
        lateinit var componentDefinition: ComponentDefinition

        init {
            parent?.children?.add(this)
        }

        fun getPath(): String {
            val simpleName = this.javaClass.simpleName
            return if(parent == null) simpleName else "${parent.getPath()}->$simpleName"
        }

        /** children last recursion */
        fun accept(f: (node: Node) -> Unit) {
            f(this)
            children.forEach { it.accept(f) }
        }

        companion object {
            fun buildTree(components: List<Component>): Node {
                val root = Node(components.find { it.parentId == null }!!)
                _buildTree(root, components)
                return root;
            }

            private fun _buildTree(parent: Node, components: List<Component>) {
                val children = components.filter { it.parentId == parent.component.id }
                children.forEach {
                    val newParent = Node(it, parent)
                    _buildTree(newParent, components)
                }
            }
        }
    }

}