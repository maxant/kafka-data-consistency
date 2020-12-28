package ch.maxant.kdc.mf.contracts.adapter

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient
import java.util.*
import javax.ws.rs.*
import javax.ws.rs.core.MediaType

@Path("/organisation")
@RegisterRestClient
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
interface OrganisationAdapter {

    @GET
    @Path("/staffByPartnerId/{partnerId}")
    fun getStaffByPartnerId(@PathParam("partnerId") partnerId: UUID): Staff
}

data class Staff(
    val partnerId: UUID,
    val un: String
)