package ch.maxant.kdc.mf.pricing.dto;

import java.util.*

data class TreeComponent(
        val componentId: String,
        val componentDefinitionId: String,
        val configs: List<Configuration>,
        val children: List<TreeComponent>
) {
    fun accept(visitor: Visitor) {
        visitor.visit(this)
        children.forEach { it.accept(visitor) }
    }
}

interface Visitor {
    fun visit(component: TreeComponent)
}

data class FlatComponent(
        val id: UUID,
        val parentId: UUID?,
        val componentDefinitionId: String,
        val configs: List<Configuration>
)
