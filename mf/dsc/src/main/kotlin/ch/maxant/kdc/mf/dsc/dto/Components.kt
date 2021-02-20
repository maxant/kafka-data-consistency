package ch.maxant.kdc.mf.dsc.dto;

import java.util.*

data class TreeComponent(
        val componentId: String,
        val componentDefinitionId: String,
        val configs: List<Configuration>,
        val children: List<TreeComponent>,
        val productId: String?
)

data class FlatComponent(
    val id: UUID,
    val parentId: UUID?,
    val componentDefinitionId: String,
    val configs: List<Configuration>,
    val productId: String?
)
