package ch.maxant.kdc.mf.contracts.boundary

import ch.maxant.kdc.mf.contracts.entity.ContractEntity
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
    fun getById(@PathParam("id") id: UUID) =
        Response.ok(em.find(ContractEntity::class.java, id)).build()

    @POST
    @Transactional
    fun create(contractEntity: ContractEntity): Response {
        em.persist(contractEntity)
        return Response.created(URI.create("/${contractEntity.id}")).entity(contractEntity).build()
    }

}