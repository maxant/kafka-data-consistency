package ch.maxant.kdc.mf.contracts.control

import ch.maxant.kdc.mf.contracts.adapter.OU
import ch.maxant.kdc.mf.contracts.adapter.OrganisationAdapter
import ch.maxant.kdc.mf.contracts.adapter.PartnerRelationshipsAdapter
import ch.maxant.kdc.mf.contracts.dto.CreatePartnerRelationshipCommand.Role
import ch.maxant.kdc.mf.library.Context
import org.eclipse.microprofile.opentracing.Traced
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.jboss.logging.Logger
import java.util.*
import javax.enterprise.context.Dependent
import javax.inject.Inject
import javax.ws.rs.NotAuthorizedException

/** A class for coping with Attribute Based Access Control. */
@Dependent
@Traced
class Abac {

    @Inject
    lateinit var context: Context

    @Inject
    @RestClient // bizarrely this doesnt work with constructor injection
    lateinit var partnerRelationshipsAdapter: PartnerRelationshipsAdapter

    @Inject
    @RestClient // bizarrely this doesnt work with constructor injection
    lateinit var organisationAdapter: OrganisationAdapter

    private val log = Logger.getLogger(this.javaClass)

    fun ensureUserIsContractHolder(contractId: UUID) {
        val partnerId = getPartnerIdOfUser()
        if(partnerId.toString() == "3cd5b3b1-e740-4533-a526-2fa274350586") {
            // john can accept all offers, because we cant currently add new partners as users, and we dont want to
            log.warn("John is acting as the contract holder!")
            return
        }
        val role = Role.CONTRACT_HOLDER
        if (!partnerRelationshipsAdapter.latestByForeignIdAndRole(contractId, role.toString())
                        .all { it.partnerId == partnerId }) {
            throw NotAuthorizedException("you are not the contract holder of contract $contractId. " +
                    "Only the contract holder may accept the contract.") // ForbiddenException's message isnt exposed by standard quarkus exception mappings :-(
        }
    }

    fun ensureUserIsSalesRep(contractId: UUID) {
        val partnerId = getPartnerIdOfUser()
        val role = Role.SALES_REP
        if (!partnerRelationshipsAdapter.latestByForeignIdAndRole(contractId, role.toString())
                        .all { it.partnerId == partnerId }) {
            throw NotAuthorizedException("you are not the sales rep of contract $contractId. " +
                    "Only the sales rep may approve the contract.") // ForbiddenException's message isnt exposed by standard quarkus exception mappings :-(
        }
    }

    fun ensureUserIsContractHolderOrUsersOuOwnsContractOrUserInHeadOffice(contractId: UUID) {
        val partnerIdOfUser = getPartnerIdOfUser()
        val latestPartnerRelationships = partnerRelationshipsAdapter.latestByForeignIdAndRole(contractId, "*")

        if(latestPartnerRelationships.filter { it.role == Role.CONTRACT_HOLDER.toString() }
                        .any { it.partnerId == partnerIdOfUser }) {
            return // user is contract holder
        }

        val headOffice = organisationAdapter.getOrganisation()
        if(headOffice.staff.any { it.un == context.user }) {
            return // user is in head office
        }

        val partnerIdsOfSalesReps = latestPartnerRelationships
                .filter { it.role == Role.SALES_REP.toString() }
                .map { it.partnerId }

        fun userIsInSameOuAsSalesRep(ou: OU): Boolean {
            if(ou.staff.map { it.partnerId }.intersect(partnerIdsOfSalesReps).isNotEmpty()) {
                // this ou contains a sales rep
                if(ou.staff.any { it.partnerId == partnerIdOfUser }) {
                    // user is also in OU
                    return true
                }
            }
            return ou.children.any { userIsInSameOuAsSalesRep(it) }
        }

        if(!userIsInSameOuAsSalesRep(headOffice)) {
            throw NotAuthorizedException("you are not a) the contract holder, b) in head office or c) " +
                    "in the same organisational unit as the sales rep of contract $contractId, so you are " +
                    "not authorised to view the contract.") // ForbiddenException's message isnt exposed by standard quarkus exception mappings :-(
        }
    }

    private fun getPartnerIdOfUser(): UUID? =
        context.jwt!!.claim<String>("partnerId").map { UUID.fromString(it) }.orElse(null)

}