package ch.maxant.kdc.mf.contracts.adapter

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient
import java.util.*
import javax.ws.rs.*
import javax.ws.rs.core.MediaType

@Path("/partner-relationships")
@RegisterRestClient
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
interface PartnerRelationshipsAdapter {

    @GET
    @Path("/validate/{contractId}")
    fun validate(
            @PathParam("contractId") foreignId: UUID,
            @QueryParam("rolesThatCanBeMissing") rolesThatCanBeMissing: List<String>
    )
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