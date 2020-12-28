package ch.maxant.kdc.mf.contracts.dto

import java.util.*

data class CreateTaskCommand(
        val referenceId: UUID,
        val userId: String,
        val title: String,
        val description: String
)