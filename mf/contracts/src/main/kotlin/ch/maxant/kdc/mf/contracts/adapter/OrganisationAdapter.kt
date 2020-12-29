package ch.maxant.kdc.mf.contracts.adapter

import com.fasterxml.jackson.annotation.JsonIgnore
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

    @GET
    fun getOrganisation(): OU
}

data class Staff(
    val partnerId: UUID,
    val un: String
)

data class OU(val staff: List<Staff>, val children: List<OU>)
