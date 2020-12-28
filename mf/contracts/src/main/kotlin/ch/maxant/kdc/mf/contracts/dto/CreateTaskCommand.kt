package ch.maxant.kdc.mf.contracts.dto

import java.util.*

data class CreateTaskCommand(
        val referenceId: UUID,
        val userId: String,
        val title: String,
        val description: String,
        val action: Action,
        val params: Map<String, String>
) {
    enum class Action {
        APPROVE_CONTRACT
    }
}

