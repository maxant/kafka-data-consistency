package ch.maxant.kdc.mf.contracts.adapter

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient
import java.util.*
import javax.ws.rs.*
import javax.ws.rs.core.MediaType

@Path("/pricing")
@RegisterRestClient
interface PricingAdapter {

    @GET
    @Path("/countNotSameSyncTime/{contractId}/{syncTimestamp}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun countNotSameSyncTime(@PathParam("contractId") contractId: UUID, @PathParam("syncTimestamp") syncTimestamp: Long): Int
}
