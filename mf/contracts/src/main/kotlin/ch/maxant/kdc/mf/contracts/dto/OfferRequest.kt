package ch.maxant.kdc.mf.contracts.dto

import ch.maxant.kdc.mf.contracts.entity.ProductId
import java.time.LocalDate

data class OfferRequest (
    var start: LocalDate,
    var productId: ProductId
) {
    constructor()
}
