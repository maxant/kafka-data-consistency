package ch.maxant.kdc.mf.contracts.dto

import ch.maxant.kdc.mf.contracts.definitions.Configuration
import ch.maxant.kdc.mf.contracts.definitions.ProductId
import java.util.*

data class Component(
    val id: UUID,
    val parentId: UUID?,
    val componentDefinitionId: String,
    val configs: List<Configuration<*>>,
    val productId: ProductId?,
    var cardinalityKey: String,
    var path: String? = null
)