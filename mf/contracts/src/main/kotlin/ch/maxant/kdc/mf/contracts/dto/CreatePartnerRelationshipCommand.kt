package ch.maxant.kdc.mf.contracts.dto

import java.time.LocalDateTime
import java.util.*

data class CreatePartnerRelationshipCommand(
        val partnerId: UUID,
        val foreignId: UUID,
        val role: Role,
        val start: LocalDateTime,
        val end: LocalDateTime,
        val additionalRelationshipsToCreate: List<Role>
) {
    enum class Role {
        SALES_REP,
        CONTRACT_HOLDER
    }
}