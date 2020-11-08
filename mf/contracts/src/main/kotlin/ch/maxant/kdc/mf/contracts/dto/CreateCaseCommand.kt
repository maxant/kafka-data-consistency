package ch.maxant.kdc.mf.contracts.dto

import java.util.*

data class CreateCaseCommand(
        val referenceId: UUID,
        val caseType: String = "SALES"
)