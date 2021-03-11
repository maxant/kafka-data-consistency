package ch.maxant.kdc.mf.contracts.dto

import ch.maxant.kdc.mf.contracts.entity.ContractEntity

data class Draft(
        val contract: ContractEntity,
        val allComponents: List<Component>
)
