package ch.maxant.kdc.mf.organisation.boundary

import ch.maxant.kdc.mf.library.Secure
import ch.maxant.kdc.mf.organisation.control.*
import com.google.common.hash.Hashing
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.jboss.logging.Logger
import java.lang.StringBuilder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import java.security.NoSuchAlgorithmException

import java.security.MessageDigest
import java.util.*
import kotlin.experimental.and


@Path("/security")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class SecurityResource {

    @Inject
    lateinit var securityDefinitions: SecurityDefinitions

    @Inject
    lateinit var tokens: Tokens

    val log: Logger = Logger.getLogger(this.javaClass)

    @GET
    @Path("/definitions")
    @Operation(summary = "gets the security configuration as a tree of processes, process steps, methods, roles, users")
    fun getSecurityConfiguration(@QueryParam("includeUsers") @Parameter(name = "includeUsers") includeUsers: Boolean?) =
        Response.ok(securityDefinitions.getDefinitions(includeUsers)).build()

    @POST // not get, because URLs are often logged, and so we want the password to be hidden
    @Path("/token/{username}/")
    @Operation(summary = "gets a JWT for the given user if the password matches the user")
    fun getToken(@Parameter(name = "username") @PathParam("username") username: String,
            @Parameter(name = "password") password: String) =
        Response.ok(tokens.generate(login(username, password))).build()

    private fun login(username: String, password: String): User {
        val f: (User) -> Boolean = { it.un == username }
        val user = Staff.values().find(f) ?: Partner.values().find(f)
        if (user == null) {
            log.info("unknown user $username")
            throw ForbiddenException() // no details, as that would be an attack point
        }
        if (hash(user.pswd) != password) {
            log.info("wrong password for user ${user.un}")
            throw ForbiddenException() // no details, as that would be an attack point
        }
        log.info("user is logged in ${user.un}")
        return user
    }

    fun hash(password: String) =
        // CryptoJS.SHA512("asdf").toString(CryptoJS.enc.Base64);
        // QBsJ6rPAE9TKVJIruAK+yP1TGBkrCnXyAdizcnQpCA+zN1kavT5ERTuVRVW3oIEuEIHDm3QCk/dl6ucx9aZe0Q==
        Base64.getEncoder().encodeToString(Hashing.sha512().hashString(password, StandardCharsets.UTF_8).asBytes())


    @GET
    @Path("/testSecurity")
    @Secure // just to check that we get a warning at boot time
    fun testSecureChecks(): Response {
        return Response.ok().build()
    }

}


