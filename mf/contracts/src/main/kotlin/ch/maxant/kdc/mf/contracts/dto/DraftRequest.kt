package ch.maxant.kdc.mf.contracts.dto

import ch.maxant.kdc.mf.contracts.definitions.ProductId
import java.time.LocalDate
import java.util.*
import javax.validation.constraints.FutureOrPresent
import javax.validation.constraints.NotNull

data class DraftRequest (

    /** propagated to all downstream processes so that events can be correlated upstream */
    @field:NotNull
    var requestId: UUID,

    @field:NotNull @field:FutureOrPresent
    var start: LocalDate,

    @field:NotNull
    var productId: ProductId,

    /** an optional contractId - if supplied, it will be honoured. */
    var contractId: UUID = UUID.randomUUID()
)
