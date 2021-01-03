package ch.maxant.kdc.mf.organisation.boundary

import ch.maxant.kdc.mf.organisation.control.OUs
import ch.maxant.kdc.mf.organisation.control.OUs.HEAD_OFFICE
import ch.maxant.kdc.mf.organisation.control.Staff
import ch.maxant.kdc.mf.organisation.control.StaffRole
import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Timed
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
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
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun getStaffRoles() =
        Response.ok(StaffRole.values()).build()

    @GET
    @Operation(summary = "gets all staff in a given role")
    @Path("/staffInRole/{role}")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun getStaffByRole(@Parameter(name = "role") @PathParam("role") role: StaffRole) =
        Response.ok(OUs.getAllStaff(role)).build()

    @GET
    @Operation(summary = "gets the staff member by the given partnerId")
    @APIResponses(
            APIResponse(description = "a staff member", responseCode = "200", content = [
                Content(mediaType = MediaType.APPLICATION_JSON, schema = Schema(implementation = Staff::class))
            ])
    )
    @Path("/staffByPartnerId/{partnerId}")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun getStaffByPartnerId(@Parameter(name = "partnerId") @PathParam("partnerId") partnerId: UUID) =
        Response.ok(OUs.getAllStaff().find { it.partnerId == partnerId }).build()

    @GET
    @Operation(summary = "gets all staff in a given role who can service the given postcode",
            description = "if none is explicitly attached to the postcode, then someone from head office takes over.")
    @Path("/staffInRole/{role}/{postcode}")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun getStaffByRoleAndPostCode(@Parameter(name = "role") @PathParam("role") role: StaffRole,
                                  @Parameter(name = "postcode") @PathParam("postcode") postcode: String): Response {
        val staffInRole = OUs.getAllStaff(role)
        return Response.ok(
                // TODO first, or someone else say based on some kind of work load criteria? we could feed back work load to this component
            staffInRole.find { it.ous.flatMap { it.postcodes }.contains(postcode) } ?: staffInRole.find { it.ous.contains(HEAD_OFFICE) }
        ).build()
    }

    @GET
    @Operation(summary = "gets the organisation as a tree")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun getOrganisation() =
        Response.ok(HEAD_OFFICE).build()

}
