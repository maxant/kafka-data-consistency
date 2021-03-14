package ch.maxant.kdc.mf.contracts.adapter

import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Timed
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

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

    @GET
    @Path("/prices/{contractId}")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun priceByContractId(@PathParam("contractId") contractId: UUID,
                          @QueryParam("dateTime") dateTimeString: String
    ): List<PriceEntity>
}

data class Price(val total: BigDecimal, val tax: BigDecimal)

data class PriceEntity(val price: BigDecimal, val tax: BigDecimal, val componentId: UUID, val pricingId: String)
