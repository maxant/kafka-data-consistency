package ch.maxant.kdc.mf.contracts.boundary

import ch.maxant.kdc.mf.contracts.adapter.ESAdapter
import ch.maxant.kdc.mf.contracts.adapter.OrganisationAdapter
import ch.maxant.kdc.mf.contracts.adapter.PartnerRelationshipsAdapter
import ch.maxant.kdc.mf.contracts.adapter.PricingAdapter
import ch.maxant.kdc.mf.contracts.control.Abac
import ch.maxant.kdc.mf.contracts.control.EventBus
import ch.maxant.kdc.mf.contracts.definitions.ProductId
import ch.maxant.kdc.mf.contracts.dto.*
import ch.maxant.kdc.mf.contracts.entity.ComponentEntity
import ch.maxant.kdc.mf.contracts.entity.ContractEntity
import ch.maxant.kdc.mf.contracts.entity.ContractState
import ch.maxant.kdc.mf.library.Context
import ch.maxant.kdc.mf.library.Secure
import ch.maxant.kdc.mf.library.doByHandlingValidationExceptions
import com.fasterxml.jackson.databind.ObjectMapper
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Timed
import org.eclipse.microprofile.openapi.annotations.Operation
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

    @Inject
    lateinit var abac: Abac

    @Inject
    lateinit var draftsResource: DraftsResource

    @Inject
    lateinit var om: ObjectMapper

    private val log = Logger.getLogger(this.javaClass)

    @GET
    @Path("/{contractId}")
    @Secure
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun getById(@PathParam("contractId") contractId: UUID,
                @QueryParam("withDetails") withDetails: Boolean = false
    ): Response {
        val contract = em.find(ContractEntity::class.java, contractId)

        abac.ensureUserIsContractHolderOrUsersOuOwnsContractOrUserInHeadOffice(contractId)

        if(withDetails) {
            contract.components = ComponentEntity
                    .Queries
                    .selectByContractId(em, contractId)
                    .map { Component(om, it) }
        }

        return Response.ok(contract).build()
    }

    @PUT
    @Path("/offerAndAccept/{contractId}")
    @Operation(summary = "convenience method for contract holder so that they can offer and accept the contract in one step. can only be executed by the contract holder.")
    @Secure
    @Transactional
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun offerDraftAndAcceptOffer(@PathParam("contractId") contractId: UUID) = doByHandlingValidationExceptions {
        abac.ensureUserIsContractHolder(contractId)
        draftsResource.offerDraft(contractId)
        this.acceptOffer(contractId)
    }

    @PUT
    @Path("/accept/{contractId}")
    @Secure
    @Transactional
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun acceptOffer(@PathParam("contractId") contractId: UUID) = doByHandlingValidationExceptions {

        abac.ensureUserIsContractHolder(contractId)

        // TODO validate like when offering? i guess only if stuff changed?

        val contract = em.find(ContractEntity::class.java, contractId)

        acceptOffer(contract)

        val components = ComponentEntity.Queries.selectByContractId(em, contractId)
        val rootComponentId = components.find { it.parentId == null }!!.id
        val productId = components.find { it.productId != null }!!.productId!!

        // TODO fix timing problems related to zones. the contract seems to be valid a couple hours longer than the prices! => take a day off below
        if(pricingAdapter.totalPrice(listOf(rootComponentId), contract.end.minusDays(1)).total > BigDecimal("30.00")) {
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
            approveContract(contract, autoApprovalUser, productId)
            log.info("auto-approved contract $contractId")
        }

        log.info("publishing AcceptedOffer event")
        eventBus.publish(AcceptedOffer(contract))

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

    private fun approveContract(contract: ContractEntity, userId: String, productId: ProductId) {
        contract.approvedAt = LocalDateTime.now()
        contract.approvedBy = userId
        contract.contractState = ContractState.APPROVED // code just for completions sake
        contract.contractState = ContractState.RUNNING

        eventBus.publish(ApprovedContract(contract, productId))
    }

    private fun getSalesRepUsername(contractId: UUID): String {
        val role = CreatePartnerRelationshipCommand.Role.SALES_REP
        val partnerId = partnerRelationshipsAdapter.latestByForeignIdAndRole(contractId, role.toString()).first().partnerId
        return organisationAdapter.getStaffByPartnerId(partnerId).un
    }

    @PUT
    @Path("/approve/{contractId}")
    @Secure
    @Transactional
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun approve(@PathParam("contractId") contractId: UUID) = doByHandlingValidationExceptions {

        abac.ensureUserIsSalesRep(contractId)

        // TODO validate like when offering? i guess only if stuff changed?

        val contract = em.find(ContractEntity::class.java, contractId)

        val components = ComponentEntity.Queries.selectByContractId(em, contractId)
        val productId = components.find { it.productId != null }!!.productId!!

        require(contract.contractState == ContractState.AWAITING_APPROVAL) {
            "Contract is not in state ${ContractState.AWAITING_APPROVAL}, but in state ${contract.contractState}"
        }

        eventBus.publish(
                CompleteTasksCommand(contract.id, CreateTaskCommand.Action.APPROVE_CONTRACT.toString())
        )

        approveContract(contract, context.user, productId)
        log.info("approved contract $contractId")

        esAdapter.updateOffer(contractId, contract.contractState)

        Response.ok(contract).build()
    }
}