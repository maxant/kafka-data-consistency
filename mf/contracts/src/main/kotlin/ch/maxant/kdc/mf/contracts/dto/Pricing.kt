package ch.maxant.kdc.mf.contracts.dto

import java.math.BigDecimal
import java.util.*

data class Price(val totalPrice: BigDecimal, val tax: BigDecimal)

data class Pricing(val componentId: UUID, val price :Price)
