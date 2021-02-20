package ch.maxant.kdc.mf.dsc.boundary

import ch.maxant.kdc.mf.dsc.entity.DiscountSurchargeEntity
import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Timed
import java.util.*
import javax.inject.Inject
import javax.persistence.EntityManager
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("/conditions")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class ConditionsResource(
    @Inject var em: EntityManager
) {

    @GET
    @Path("/countNotSameSyncTime/{contractId}/{syncTimestamp}")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun validateSyncTime(@PathParam("contractId") contractId: UUID, @PathParam("syncTimestamp") syncTimestamp: Long) =
        Response.ok(DiscountSurchargeEntity.Queries.countByContractIdAndNotSyncTimestamp(em, contractId, syncTimestamp)).build()

}
