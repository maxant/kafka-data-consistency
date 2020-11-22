package ch.maxant.kdc.mf.pricing.boundary

import ch.maxant.kdc.mf.pricing.entity.PriceEntity
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import java.util.*
import javax.inject.Inject
import javax.persistence.EntityManager
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("/pricing")
@Tag(name = "pricing")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class PricingResource(
    @Inject
    public var em: EntityManager
) {

    @GET
    @Path("/validateSyncTime/{contractId}/{syncTimestamp}")
    fun validateSyncTime(@PathParam("contractId") contractId: UUID, @PathParam("syncTimestamp") syncTimestamp: Long) =
            Response.ok(PriceEntity.Queries.countByContractIdAndSyncTimestamp(em, contractId, syncTimestamp)).build()

}