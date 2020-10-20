package ch.maxant.kdc.mf.contracts.boundary

import ch.maxant.kdc.mf.contracts.entity.Contract
import java.net.URI
import java.util.*
import javax.inject.Inject
import javax.persistence.EntityManager
import javax.transaction.Transactional
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("/contracts")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class ContractResource(
    @Inject
    public var em: EntityManager
) {

    @GET
    @Path("/{id}")
    fun getById(@PathParam("id") id: UUID) =
        Response.ok(em.find(Contract::class.java, id)).build()

    @POST
    @Transactional
    fun create(contract: Contract): Response {
        em.persist(contract)
        return Response.created(URI.create("/${contract.id}")).entity(contract).build()
    }
}