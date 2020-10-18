package ch.maxant.kdc.mf.contracts

import javax.inject.Inject
import javax.persistence.EntityManager
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("/contracts")
class ContractResource {

    @Inject
    var em: EntityManager? = null

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun hello(): Response {
        return Response.ok(em?.createNativeQuery("show tables")?.resultList).build()
    }
}