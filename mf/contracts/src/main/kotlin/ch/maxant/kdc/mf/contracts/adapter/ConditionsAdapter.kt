package ch.maxant.kdc.mf.contracts.adapter

import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Timed
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*
import javax.ws.rs.*
import javax.ws.rs.core.MediaType

@Path("/conditions")
@RegisterRestClient
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
interface ConditionsAdapter {

    @GET
    @Path("/countNotSameSyncTime/{contractId}/{syncTimestamp}")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun countNotSameSyncTime(@PathParam("contractId") contractId: UUID, @PathParam("syncTimestamp") syncTimestamp: Long): Int

}

