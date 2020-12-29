package ch.maxant.kdc.mf.contracts.control

import ch.maxant.kdc.mf.contracts.adapter.PartnerRelationshipsAdapter
import ch.maxant.kdc.mf.contracts.dto.CreatePartnerRelationshipCommand
import ch.maxant.kdc.mf.library.Context
import org.eclipse.microprofile.rest.client.inject.RestClient
import java.util.*
import javax.enterprise.context.Dependent
import javax.inject.Inject
import javax.ws.rs.NotAuthorizedException

/** A class for coping with Attribute Based Access Control. */
@Dependent
class Abac {

    @Inject
    lateinit var context: Context

    @Inject
    @RestClient // bizarrely this doesnt work with constructor injection
    lateinit var partnerRelationshipsAdapter: PartnerRelationshipsAdapter

    fun ensureUserIsContractHolder(contractId: UUID) {
        val partnerId = getPartnerIdOfUser()
        val role = CreatePartnerRelationshipCommand.Role.CONTRACT_HOLDER
        if (!partnerRelationshipsAdapter.latestByForeignIdAndRole(contractId, role)
                        .all { it.partnerId.toString() == partnerId }) {
            throw NotAuthorizedException("you are not the contract holder of contract $contractId. " +
                    "Only the contract holder may accept the contract.")
        }
    }

    fun ensureUserIsSalesRep(contractId: UUID) {
        val partnerId = getPartnerIdOfUser()
        val role = CreatePartnerRelationshipCommand.Role.SALES_REP
        if (!partnerRelationshipsAdapter.latestByForeignIdAndRole(contractId, role)
                        .all { it.partnerId.toString() == partnerId }) {
            throw NotAuthorizedException("you are not the sales rep of contract $contractId. " +
                    "Only the sales rep may approve the contract.")
        }
    }

    fun ensureUserIsContractHolderOrUsersOuOwnsContractOrUserInHeadOffice(contractId: UUID) {
        try {
            ensureUserIsContractHolder(contractId)
        } catch (e: NotAuthorizedException) {

        }
/* TODO        CUSTOMER_OWNS_CONTRACT,
        OU_OWNS_CONTRACT,
        USER_IN_HEAD_OFFICE])
   */
    }

    private fun getPartnerIdOfUser(): String? {
        val partnerId = context.jwt!!.claim<String>("partnerId").orElse(null)
        return partnerId
    }

}