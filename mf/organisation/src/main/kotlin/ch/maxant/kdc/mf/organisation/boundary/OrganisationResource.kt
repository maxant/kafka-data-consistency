package ch.maxant.kdc.mf.organisation.boundary

import ch.maxant.kdc.mf.organisation.control.OUs
import ch.maxant.kdc.mf.organisation.control.OUs.HEAD_OFFICE
import ch.maxant.kdc.mf.organisation.control.StaffRole
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import java.util.*
import javax.inject.Inject
import javax.persistence.EntityManager
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("/organisation")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class OrganisationResource {

    @GET
    @Operation(summary = "gets all staff roles")
    @Path("/staffRoles")
    fun getStaffRoles() =
        Response.ok(StaffRole.values()).build()

    @GET
    @Operation(summary = "gets all staff in a given role")
    @Path("/staffInRole/{role}")
    fun getStaffByRole(@Parameter(name = "role") @PathParam("role") role: StaffRole) =
        Response.ok(OUs.getAllStaff(role)).build()

    @GET
    @Operation(summary = "gets the organisation as a tree")
    fun getOrganisation() =
        Response.ok(HEAD_OFFICE).build()

}
