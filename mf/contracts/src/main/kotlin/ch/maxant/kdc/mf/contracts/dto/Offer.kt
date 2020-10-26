package ch.maxant.kdc.mf.contracts.dto

import ch.maxant.kdc.mf.contracts.definitions.Packaging
import ch.maxant.kdc.mf.contracts.entity.ContractEntity

data class Offer(
        val contract: ContractEntity,
        val pack: Packaging
)
