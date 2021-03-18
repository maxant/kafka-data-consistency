package ch.maxant.kdc.mf.contracts.dto

import ch.maxant.kdc.mf.contracts.entity.ContractEntity
import java.math.BigDecimal
import java.util.*

data class Draft(
        val contract: ContractEntity,
        val allComponents: List<Component>,
        val persist: Boolean,
        val manualDiscountsSurcharges: List<ManualDiscountSurcharge> = emptyList()
)

data class ManualDiscountSurcharge(
    val componentId: UUID,
    val value: BigDecimal
)