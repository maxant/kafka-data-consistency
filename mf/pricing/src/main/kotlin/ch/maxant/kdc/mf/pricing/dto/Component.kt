package ch.maxant.kdc.mf.pricing.dto;

data class Component(
        val componentId: String,
        val componentDefinitionId: String,
        val configs: List<Configuration>,
        val children: List<Component>
) {
    fun accept(visitor: Visitor) {
        visitor.visit(this)
        children.forEach { it.accept(visitor) }
    }
}

interface Visitor {
    fun visit(component: Component)
}