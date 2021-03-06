package ch.maxant.kdc.mf.contracts.adapter

import ch.maxant.kdc.mf.contracts.boundary.query.DiscountSurcharge
import com.fasterxml.jackson.databind.node.ArrayNode
import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Timed
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient
import java.util.*
import javax.ws.rs.*
import javax.ws.rs.core.MediaType

@Path("/discountssurcharges")
@RegisterRestClient
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
interface DiscountsSurchargesAdapter {

    @GET
    @Path("/countNotSameSyncTime/{contractId}/{syncTimestamp}")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun countNotSameSyncTime(@PathParam("contractId") contractId: UUID, @PathParam("syncTimestamp") syncTimestamp: Long): Int

    @GET
    @Path("/{contractId}")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun getByContractIdAsArrayNode(@PathParam("contractId") contractId: UUID): ArrayNode

    @GET
    @Path("/{contractId}")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun getByContractIdAsString(@PathParam("contractId") contractId: UUID): String

    @GET
    @Path("/{contractId}")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun getByContractIdAsDto(@PathParam("contractId") contractId: UUID): List<DiscountSurcharge>
}

