package ch.maxant.kdc.mf.pricing.dto;

data class TreeComponent(
        val componentId: String,
        val componentDefinitionId: String,
        val configs: List<Configuration>,
        val children: List<TreeComponent>,
        val productId: String? = null
) {
    /** kids first, so that we price from the bottom upwards */
    fun accept(visitor: Visitor) {
        children.forEach { it.accept(visitor) }
        visitor.visit(this)
    }
}

interface Visitor {
    fun visit(component: TreeComponent)
}

