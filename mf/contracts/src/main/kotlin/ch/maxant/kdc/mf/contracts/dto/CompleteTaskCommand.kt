package ch.maxant.kdc.mf.contracts.dto

import java.util.*

data class CompleteTasksCommand(
        val referenceId: UUID,
        val action: String
)
