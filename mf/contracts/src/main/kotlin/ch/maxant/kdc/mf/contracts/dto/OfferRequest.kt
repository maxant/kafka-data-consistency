package ch.maxant.kdc.mf.contracts.dto

import ch.maxant.kdc.mf.contracts.definitions.ProductId
import java.time.LocalDate
import javax.validation.constraints.FutureOrPresent
import javax.validation.constraints.NotNull

data class OfferRequest (
    @field:NotNull @field:FutureOrPresent
    var start: LocalDate,

    @field:NotNull
    var productId: ProductId
) {
    constructor() : this(LocalDate.now(), ProductId.COOKIES_MILKSHAKE)
}
