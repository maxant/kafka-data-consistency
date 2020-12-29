package ch.maxant.kdc.mf.contracts.adapter

import ch.maxant.kdc.mf.contracts.dto.CreatePartnerRelationshipCommand
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
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

    @GET
    @Path("/latestByForeignId/{foreignId}/{role}")
    fun latestByForeignIdAndRole(
            @PathParam("foreignId") foreignId: UUID,
            @PathParam("role") role: String,
            @QueryParam("idsOnly") idsOnly: Boolean = false
    ): List<PartnerRelationship>
}

data class PartnerRelationship(
        val partnerId: UUID,
        val role: String
)
