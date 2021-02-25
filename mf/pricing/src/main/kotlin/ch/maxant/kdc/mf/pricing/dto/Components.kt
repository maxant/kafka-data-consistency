package ch.maxant.kdc.mf.pricing.dto;

data class TreeComponent(
        val componentId: String,
        val componentDefinitionId: String,
        val configs: List<Configuration>,
        val children: List<TreeComponent>,
        val productId: String?
) {
    fun accept(visitor: Visitor) {
        visitor.visit(this)
        children.forEach { it.accept(visitor) }
    }
}

interface Visitor {
    fun visit(component: TreeComponent)
}

