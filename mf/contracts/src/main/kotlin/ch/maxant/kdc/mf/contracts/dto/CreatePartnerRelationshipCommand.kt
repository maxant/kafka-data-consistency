package ch.maxant.kdc.mf.contracts.dto

import java.time.LocalDateTime
import java.util.*

data class CreatePartnerRelationshipCommand(
        val partnerId: UUID,
        val foreignId: UUID,
        val role: String,
        val start: LocalDateTime,
        val end: LocalDateTime
)