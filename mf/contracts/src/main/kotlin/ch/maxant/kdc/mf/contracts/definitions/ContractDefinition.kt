package ch.maxant.kdc.mf.contracts.definitions

import java.lang.RuntimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

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
                        // TODO components definition
                        // TODO conditions definition
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
                        .orElseThrow { RuntimeException("No matching contract definition - try a different date") }
    }
}