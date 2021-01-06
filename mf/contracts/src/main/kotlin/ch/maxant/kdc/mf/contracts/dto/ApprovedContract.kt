package ch.maxant.kdc.mf.contracts.dto

import ch.maxant.kdc.mf.contracts.definitions.ProductId
import ch.maxant.kdc.mf.contracts.entity.ContractEntity

data class ApprovedContract(
        val contract: ContractEntity,
        val productId: ProductId
)

