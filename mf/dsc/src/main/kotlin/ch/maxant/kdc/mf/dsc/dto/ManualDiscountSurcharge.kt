package ch.maxant.kdc.mf.dsc.dto

import java.math.BigDecimal
import java.util.*

data class ManualDiscountSurcharge(
    val componentId: UUID,
    val value: BigDecimal
)