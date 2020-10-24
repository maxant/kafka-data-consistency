package ch.maxant.kdc.mf.contracts.dto

import ch.maxant.kdc.mf.contracts.definitions.ProductId
import java.time.LocalDate

data class OfferRequest (
    var start: LocalDate,
    var productId: ProductId
) {
    constructor() : this(LocalDate.MIN, ProductId.COOKIES_MILKSHAKE)
}
