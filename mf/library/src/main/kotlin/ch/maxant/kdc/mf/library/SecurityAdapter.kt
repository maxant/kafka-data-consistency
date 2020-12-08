package ch.maxant.kdc.mf.library

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient
import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType

@Path("/security")
@RegisterRestClient
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
interface SecurityAdapter {

    @GET
    @Path("/definitions")
    fun getSecurityConfiguration(): SecurityDefinitionResponse
}

data class SecurityDefinitionResponse(val root: List<Node>)
data class Node(val key: String, val data: Data, val children: List<Node>)
data class Data(val roleMappings: String? = null, val users: List<String> = emptyList(), val methods: Set<String> = emptySet())
