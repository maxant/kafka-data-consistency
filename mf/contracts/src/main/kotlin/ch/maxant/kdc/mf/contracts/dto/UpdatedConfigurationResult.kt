package ch.maxant.kdc.mf.contracts.dto

import ch.maxant.kdc.mf.contracts.definitions.ComponentDefinition
import ch.maxant.kdc.mf.contracts.entity.ContractEntity

data class UpdatedDraft(
        val contract: ContractEntity,
        val allComponents: List<ComponentDefinition>
)
