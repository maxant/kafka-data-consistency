package ch.maxant.kdc.mf.billing.dto

import java.time.LocalDate
import java.util.*

data class SelectionDto(val contracts: List<ContractDto>, val id: UUID = UUID.randomUUID())

data class ContractDto(val id: UUID, val basePeriodsToPrice: List<Period>, val periodsToBill: List<Period>)

data class Period(val from: LocalDate, val to: LocalDate)
