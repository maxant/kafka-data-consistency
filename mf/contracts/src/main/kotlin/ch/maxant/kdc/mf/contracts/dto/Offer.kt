package ch.maxant.kdc.mf.contracts.dto

import ch.maxant.kdc.mf.contracts.definitions.Product
import ch.maxant.kdc.mf.contracts.entity.ContractEntity

data class Offer(
        val contract: ContractEntity,
        val product: Product,
        val pricing: Pricing
)
