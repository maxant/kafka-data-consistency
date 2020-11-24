package ch.maxant.kdc.mf.contracts.adapter

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient
import java.util.*
import javax.ws.rs.*
import javax.ws.rs.core.MediaType

@Path("/partner-relationships")
@RegisterRestClient
interface PartnerAdapter {

    @GET
    @Path("/latest/{contractId}/{role}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun getPartnersInRole(@PathParam("contractId") contractId: UUID,
                          @PathParam("role") role: String): List<PartnerRelationship>
}

data class PartnerRelationship(
        val partner: Partner,
        val role: String
)
data class Partner(
        val id: UUID,
        val firstName: String,
        val lastName: String
)