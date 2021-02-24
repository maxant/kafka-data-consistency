package ch.maxant.kdc.mf.contracts.definitions

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.validation.ValidationException

class ContractDefinition(
        val sellableFrom: LocalDateTime,
        val sellableTo: LocalDateTime,
        val startFrom: LocalDateTime,
        val startTo: LocalDateTime,
        val productId: ProductId,
        val defaultDurationDays: Long
) {

    companion object {
        private val definitions: List<ContractDefinition> = listOf(
                ContractDefinition(LocalDate.of(2020, 10, 1).atStartOfDay(),
                        LocalDate.of(10000, 1, 1).atTime(LocalTime.MIDNIGHT),
                        LocalDate.of(2020, 10, 1).atStartOfDay(),
                        LocalDate.of(10000, 1, 1).atTime(LocalTime.MIDNIGHT),
                        ProductId.COOKIES_MILKSHAKE,
                        720
                        // TODO components definition release number
                        // TODO conditions definition release number
                ),
                ContractDefinition(LocalDate.of(2021, 2, 1).atStartOfDay(),
                        LocalDate.of(10000, 1, 1).atTime(LocalTime.MIDNIGHT),
                        LocalDate.of(2021, 2, 1).atStartOfDay(),
                        LocalDate.of(10000, 1, 1).atTime(LocalTime.MIDNIGHT),
                        ProductId.COFFEE_LATTE_SKINNY,
                        390
                        // TODO components definition release number
                        // TODO conditions definition release number
                )
        )

        fun find(productId: ProductId, start: LocalDateTime): ContractDefinition =
                definitions.stream()
                        .filter {
                            it.sellableFrom.isBefore(LocalDateTime.now()) &&
                                    it.sellableTo.isAfter(LocalDateTime.now()) &&
                                    it.startFrom.isBefore(start) &&
                                    it.startTo.isAfter(start) &&
                                    it.productId == productId
                        }.findFirst()
                        .orElseThrow { ValidationException("No matching contract definition - try a different date or product") }
    }
}