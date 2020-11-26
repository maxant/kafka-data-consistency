package ch.maxant.kdc.mf.contracts.dto

import java.util.*

data class CreatePartnerRelationshipCommand(
        val partnerId: String,
        val contractId: UUID,
        val role: String
)