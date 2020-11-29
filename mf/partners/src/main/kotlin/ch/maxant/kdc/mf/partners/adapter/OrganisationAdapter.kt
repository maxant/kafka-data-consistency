package ch.maxant.kdc.mf.partners.adapter

import ch.maxant.kdc.mf.partners.entity.Role
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient
import java.util.*
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("/organisation")
@RegisterRestClient
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
interface OrganisationAdapter {

    @GET
    @Path("/staffInRole/{role}/{postcode}")
    fun getStaffByRoleAndPostCode(@PathParam("role") role: Role, @PathParam("postcode") postcode: String): Staff
}

data class Staff(
    val partnerId: UUID
)