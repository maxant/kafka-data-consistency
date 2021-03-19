package ch.maxant.kdc.mf.contracts.dto

import ch.maxant.kdc.mf.contracts.entity.ContractEntity
import java.math.BigDecimal
import java.util.*

data class SetDiscountCommand(
    val contract: ContractEntity,
    val allComponents: List<Component>,
    val componentId: UUID,
    val value: BigDecimal,
    val persist: Boolean
)