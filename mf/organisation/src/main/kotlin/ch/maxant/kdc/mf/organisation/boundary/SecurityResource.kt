package ch.maxant.kdc.mf.organisation.boundary

import ch.maxant.kdc.mf.organisation.control.Partner
import ch.maxant.kdc.mf.organisation.control.SecurityDefinitions
import ch.maxant.kdc.mf.organisation.control.Staff
import ch.maxant.kdc.mf.organisation.control.Tokens
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import javax.inject.Inject
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("/security")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class SecurityResource {

    @Inject
    lateinit var securityDefinitions: SecurityDefinitions

    @Inject
    lateinit var tokens: Tokens

    @GET
    @Path("/definitions")
    @Operation(summary = "gets the security configuration as a tree of processes, process steps, methods, roles, users")
    fun getSecurityConfiguration() =
        Response.ok(securityDefinitions.getDefinitions()).build()

    @GET
    @Path("/token/staff/{staffUsername}")
    @Operation(summary = "gets a JWT for the given staff member")
    fun getTokenStaff(@Parameter(name = "staffUsername") @PathParam("staffUsername") staffUsername: String) =
        Response.ok(tokens.generate(Staff.values().find { it.un == staffUsername } ?: throw NotFoundException())).build()

    @GET
    @Path("/token/partner/{partnerUsername}")
    @Operation(summary = "gets a JWT for the given partner")
    fun getTokenPartner(@Parameter(name = "partnerUsername") @PathParam("partnerUsername") partnerUsername: String) =
        Response.ok(tokens.generate(Partner.values().find { it.un == partnerUsername } ?: throw NotFoundException())).build()

    @GET
    @Path("/test")
    fun test() = Response.ok(Partner.values()).build()

}
