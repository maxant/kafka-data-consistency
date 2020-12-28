package ch.maxant.kdc.mf.contracts.boundary

import ch.maxant.kdc.mf.contracts.adapter.ESAdapter
import ch.maxant.kdc.mf.contracts.adapter.OrganisationAdapter
import ch.maxant.kdc.mf.contracts.adapter.PartnerRelationshipsAdapter
import ch.maxant.kdc.mf.contracts.adapter.PricingAdapter
import ch.maxant.kdc.mf.contracts.control.EventBus
import ch.maxant.kdc.mf.contracts.dto.CompleteTasksCommand
import ch.maxant.kdc.mf.contracts.dto.CreatePartnerRelationshipCommand
import ch.maxant.kdc.mf.contracts.dto.CreateTaskCommand
import ch.maxant.kdc.mf.contracts.entity.ComponentEntity
import ch.maxant.kdc.mf.contracts.entity.ContractEntity
import ch.maxant.kdc.mf.contracts.entity.ContractState
import ch.maxant.kdc.mf.library.Context
import ch.maxant.kdc.mf.library.Secure
import ch.maxant.kdc.mf.library.doByHandlingValidationExceptions
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.jboss.logging.Logger
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*
import javax.inject.Inject
import javax.persistence.EntityManager
import javax.transaction.Transactional
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("/contracts")
@Tag(name = "contracts")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class ContractResource(
    @Inject
    var em: EntityManager
) {
    @Inject
    lateinit var context: Context

    @Inject
    @RestClient // bizarrely this doesnt work with constructor injection
    lateinit var partnerRelationshipsAdapter: PartnerRelationshipsAdapter

    @Inject
    @RestClient // bizarrely this doesnt work with constructor injection
    lateinit var pricingAdapter: PricingAdapter

    @Inject
    @RestClient // bizarrely this doesnt work with constructor injection
    lateinit var organisationAdapter: OrganisationAdapter

    @ConfigProperty(name = "ch.maxant.kdc.mf.contracts.auto-approval-user", defaultValue = "auto-approval-user")
    lateinit var autoApprovalUser: String

    @Inject
    lateinit var eventBus: EventBus

    @Inject
    lateinit var esAdapter: ESAdapter

    private val log = Logger.getLogger(this.javaClass)

    @GET
    @Path("/{contractId}")
    @Secure
    fun getById(@PathParam("contractId") contractId: UUID): Response {
        val contract = em.find(ContractEntity::class.java, contractId)
/* TODO        CUSTOMER_OWNS_CONTRACT,
        OU_OWNS_CONTRACT,
        USER_IN_HEAD_OFFICE])
   */
        return Response.ok(contract).build()
    }

    @PUT
    @Path("/accept/{contractId}")
    @Secure
    @Transactional
    fun acceptOffer(@PathParam("contractId") contractId: UUID) = doByHandlingValidationExceptions {

        abacEnsureUserIsContractHolder(contractId)

        // TODO validate like when offering? i guess only if stuff changed?

        val contract = em.find(ContractEntity::class.java, contractId)

        acceptOffer(contract)

        val rootComponentId = ComponentEntity.Queries.selectByContractId(em, contractId).find { it.parentId == null }!!.id

        if(pricingAdapter.totalPrice(listOf(rootComponentId), contract.end).total > BigDecimal("3.00")) {
            contract.contractState = ContractState.AWAITING_APPROVAL
            log.info("contract $contractId too expensive for auto-approval")
            eventBus.publish(
                    CreateTaskCommand(contract.id, getSalesRepUsername(contractId),
                            "Approve Contract", "Please approve contract $contractId",
                            CreateTaskCommand.Action.APPROVE_CONTRACT,
                            mapOf(Pair("contractId", contractId.toString()))
                    )
            )
        } else {
            approveContract(contract, autoApprovalUser)
            log.info("auto-approved contract $contractId")
        }

        // TODO use transactional outbox
        esAdapter.updateOffer(contractId, contract.contractState)

        Response.ok(contract).build()
    }

    private fun acceptOffer(contract: ContractEntity) {
        require(contract.contractState == ContractState.OFFERED) {
            "Contract is not in state ${ContractState.OFFERED}, but in state ${contract.contractState}"
        }
        contract.acceptedAt = LocalDateTime.now()
        contract.acceptedBy = context.user
        contract.contractState = ContractState.ACCEPTED
    }

    private fun approveContract(contract: ContractEntity, userId: String) {
        contract.approvedAt = LocalDateTime.now()
        contract.approvedBy = userId
        contract.contractState = ContractState.APPROVED // code just for completions sake
        contract.contractState = ContractState.RUNNING
    }

    private fun abacEnsureUserIsContractHolder(contractId: UUID) {
        val partnerId = context.jwt!!.claim<String>("partnerId").orElse(null)
        val role = CreatePartnerRelationshipCommand.Role.CONTRACT_HOLDER
        if (!partnerRelationshipsAdapter.latestByForeignIdAndRole(contractId, role)
                        .all { it.partnerId.toString() == partnerId }) {
            throw NotAuthorizedException("you are not the contract holder of contract $contractId. " +
                    "Only the contract holder may accept the contract.")
        }
    }

    private fun abacEnsureUserIsSalesRep(contractId: UUID) {
        val partnerId = context.jwt!!.claim<String>("partnerId").orElse(null)
        val role = CreatePartnerRelationshipCommand.Role.SALES_REP
        if (!partnerRelationshipsAdapter.latestByForeignIdAndRole(contractId, role)
                        .all { it.partnerId.toString() == partnerId }) {
            throw NotAuthorizedException("you are not the sales rep of contract $contractId. " +
                    "Only the sales rep may approve the contract.")
        }
    }

    private fun getSalesRepUsername(contractId: UUID): String {
        val role = CreatePartnerRelationshipCommand.Role.SALES_REP
        val partnerId = partnerRelationshipsAdapter.latestByForeignIdAndRole(contractId, role).first().partnerId
        return organisationAdapter.getStaffByPartnerId(partnerId).un
    }

    @PUT
    @Path("/approve/{contractId}")
    @Secure
    @Transactional
    fun approve(@PathParam("contractId") contractId: UUID) = doByHandlingValidationExceptions {

        abacEnsureUserIsSalesRep(contractId)

        // TODO validate like when offering? i guess only if stuff changed?

        val contract = em.find(ContractEntity::class.java, contractId)

        require(contract.contractState == ContractState.AWAITING_APPROVAL) {
            "Contract is not in state ${ContractState.AWAITING_APPROVAL}, but in state ${contract.contractState}"
        }

        eventBus.publish(
                CompleteTasksCommand(contract.id, CreateTaskCommand.Action.APPROVE_CONTRACT.toString())
        )

        approveContract(contract, context.user)
        log.info("approved contract $contractId")

        // TODO use transactional outbox
        esAdapter.updateOffer(contractId, contract.contractState)

        Response.ok(contract).build()
    }
}