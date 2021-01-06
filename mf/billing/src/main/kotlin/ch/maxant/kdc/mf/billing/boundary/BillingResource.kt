package ch.maxant.kdc.mf.billing.boundary

import java.util.*
import javax.inject.Inject
import javax.persistence.EntityManager
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("/billing")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class BillingResource(
    @Inject
    public var em: EntityManager
) {

    @GET
    @Path("/{id}")
    fun getById(@PathParam("id") id: UUID) =
        Response.ok("id").build()

}
