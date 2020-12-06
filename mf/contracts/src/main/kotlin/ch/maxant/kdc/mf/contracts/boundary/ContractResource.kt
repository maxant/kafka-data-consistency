package ch.maxant.kdc.mf.contracts.boundary

import ch.maxant.kdc.mf.contracts.entity.ContractEntity
import ch.maxant.kdc.mf.contracts.entity.ContractState
import ch.maxant.kdc.mf.library.Secure
import ch.maxant.kdc.mf.library.doByHandlingValidationExceptions
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import java.net.URI
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
    public var em: EntityManager
) {

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
    fun acceptOffer(@PathParam("id") id: UUID) = doByHandlingValidationExceptions {
        val contract = em.find(ContractEntity::class.java, id)
        require(contract.contractState == ContractState.OFFERED) { "Contract is not in state ${ContractState.OFFERED}, but in state ${contract.contractState}" }
        contract.contractState = ContractState.ACCEPTED
        // TODO validate like when offering? i guess only if stuff changed?
        // TODO execute business rules e.g. if total is higher than customers credit limit, then we need to go thru the approval process
// TODO @SecurityCheck(attributeChecks = [CUSTOMER_OWNS_CONTRACT])
        Response.ok(contract).build()
    }

    // TODO who calls this?!?!
    @POST
    @Transactional
    fun create(contractEntity: ContractEntity): Response {
        em.persist(contractEntity)
        return Response.created(URI.create("/${contractEntity.id}")).entity(contractEntity).build()
    }

}