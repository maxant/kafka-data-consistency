package ch.maxant.kdc.mf.contracts.dto

import ch.maxant.kdc.mf.contracts.definitions.ProductId
import java.time.LocalDate
import java.util.*
import javax.validation.constraints.FutureOrPresent
import javax.validation.constraints.NotNull

data class OfferRequest (
    @field:NotNull @field:FutureOrPresent
    var start: LocalDate,

    @field:NotNull
    var productId: ProductId,

    /**
     * an optional contractId - if supplied, it will be honoured. helps with timing issues so that you can
     * subscribe to changes on this contract, before actually creating it. otherwise you might subscribe
     * after events are sent
     */
    var contractId: UUID = UUID.randomUUID()
)
