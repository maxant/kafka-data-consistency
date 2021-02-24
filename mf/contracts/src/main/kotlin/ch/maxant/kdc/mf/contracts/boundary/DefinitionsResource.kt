package ch.maxant.kdc.mf.contracts.boundary

import ch.maxant.kdc.mf.contracts.definitions.ComponentDefinition
import ch.maxant.kdc.mf.contracts.definitions.ProductId
import ch.maxant.kdc.mf.contracts.definitions.Products
import ch.maxant.kdc.mf.contracts.definitions.UnknownProductException
import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Timed
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response


@Path("/definitions")
@Tag(name = "definintions")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class DefinitionsResource {

    @Operation(summary = "Get Products")
    @APIResponses(
            APIResponse(description = "gets a list of available products", responseCode = "200", content = [
                Content(mediaType = MediaType.APPLICATION_JSON, schema = Schema(implementation = ProductId::class))
            ])
    )
    @GET
    @Path("/products")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun getProducts() = Response.ok(ProductId.values()).build()

    @Operation(summary = "Get Components")
    @APIResponses(
            APIResponse(description = "gets a list of component definitions", responseCode = "200", content = [
                Content(mediaType = MediaType.APPLICATION_JSON, schema = Schema(implementation = ProductId::class))
            ])
    )
    @GET
    @Path("/components")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun getComponents(): Response {
        val componentDefinitions = ProductId.values().map {
            try {
                it to Products.find(it, 1)
            } catch (e: UnknownProductException) {
                it to null // not supported yet
            }
        }.toMap()
        return Response.ok(componentDefinitions).build()
    }

}

