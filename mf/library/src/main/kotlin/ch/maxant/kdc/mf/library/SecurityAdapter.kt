package ch.maxant.kdc.mf.library

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient
import javax.ws.rs.*
import javax.ws.rs.core.MediaType

/** skip users, as a) we trust the JWT as it's signed and b) there'd be way too many of them! */
@Path("/security")
@RegisterRestClient
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
interface SecurityAdapter {

    @GET
    @Path("/definitions")
    fun getSecurityConfiguration(@QueryParam("includeUsers") includeUsers: Boolean = false): SecurityDefinitionResponse
}

data class SecurityDefinitionResponse(val root: List<Node>)
data class Node(val key: String, val data: Data, val children: List<Node>)
data class Data(val roleMappings: String? = null, val methods: Set<String> = emptySet())
