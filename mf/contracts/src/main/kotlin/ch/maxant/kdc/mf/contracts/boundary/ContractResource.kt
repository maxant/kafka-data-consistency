package ch.maxant.kdc.mf.contracts.boundary

import ch.maxant.kdc.mf.contracts.adapter.PartnerRelationshipsAdapter
import ch.maxant.kdc.mf.contracts.adapter.PricingAdapter
import ch.maxant.kdc.mf.contracts.dto.CreatePartnerRelationshipCommand
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
import java.net.URI
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

    @ConfigProperty(name = "ch.maxant.kdc.mf.contracts.auto-approval-user", defaultValue = "auto-approval-user")
    lateinit var autoApprovalUser: String

    private val log = Logger.getLogger(this.javaClass)

    @GET
    @Path("/{id}")
    @Secure
    fun getById(@PathParam("id") id: UUID): Response {
        val contract = em.find(ContractEntity::class.java, id)
/* TODO        CUSTOMER_OWNS_CONTRACT,
        OU_OWNS_CONTRACT,
        USER_IN_HEAD_OFFICE])
   */
        return Response.ok(contract).build()
    }

    @PUT
    @Path("/accept/{id}")
    @Secure
    @Transactional
    fun acceptOffer(@PathParam("id") id: UUID) = doByHandlingValidationExceptions {

        abacEnsureUserIsContractHolder(id)

        // TODO validate like when offering? i guess only if stuff changed?

        val contract = em.find(ContractEntity::class.java, id)

        acceptOffer(contract)

        val rootComponentId = ComponentEntity.Queries.selectByContractId(em, id).find { it.parentId == null }!!.id

        if(pricingAdapter.totalPrice(listOf(rootComponentId), contract.end).total > BigDecimal("3.00")) {
            contract.contractState = ContractState.AWAITING_APPROVAL
            log.info("contract $id too expensive for auto-approval")
            // TODO create task. assign to ?? based on org!
        } else {
            approveContract(contract, autoApprovalUser)
            log.info("auto-approved contract $id")
        }

        Response.ok(contract).build()
    }

    private fun acceptOffer(contract: ContractEntity) {
        require(contract.contractState == ContractState.OFFERED) { "Contract is not in state ${ContractState.OFFERED}, but in state ${contract.contractState}" }
        contract.acceptedAt = LocalDateTime.now()
        contract.acceptedBy = context.user
        contract.contractState = ContractState.ACCEPTED
    }

    // TODO reuse this function when an employee approves a contract
    private fun approveContract(contract: ContractEntity, autoApprovalUser: String) {
        contract.approvedAt = LocalDateTime.now()
        contract.approvedBy = autoApprovalUser
        contract.contractState = ContractState.APPROVED // code just for completions sake
        contract.contractState = ContractState.RUNNING
    }

    private fun abacEnsureUserIsContractHolder(id: UUID) {
        val partnerId = context.jwt!!.claim<String>("partnerId").orElse(null)
        val role = CreatePartnerRelationshipCommand.Role.CONTRACT_HOLDER
        if (!partnerRelationshipsAdapter.latestByForeignIdAndRole(id, role)
                        .all { it.partnerId.toString() == partnerId }) {
            throw NotAuthorizedException("you are not the contract holder of contract $id. " +
                    "Only the contract holder may accept the contract.")
        }
    }

    // TODO who calls this?!?! delete it
    @POST
    @Transactional
    fun create(contractEntity: ContractEntity): Response {
        em.persist(contractEntity)
        return Response.created(URI.create("/${contractEntity.id}")).entity(contractEntity).build()
    }

}