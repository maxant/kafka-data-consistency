package ch.maxant.kdc.mf.contracts.adapter

import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Timed
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*
import javax.ws.rs.*
import javax.ws.rs.core.MediaType

@Path("/pricing")
@RegisterRestClient
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
interface PricingAdapter {

    @GET
    @Path("/countNotSameSyncTime/{contractId}/{syncTimestamp}")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun countNotSameSyncTime(@PathParam("contractId") contractId: UUID, @PathParam("syncTimestamp") syncTimestamp: Long): Int

    @GET
    @Path("/totalPrice")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun totalPrice(@QueryParam("componentIds") componentIds: List<UUID>,
                   @QueryParam("dateTime") dateTime: LocalDateTime
    ): Price


}

data class Price(val total: BigDecimal, val tax: BigDecimal)
