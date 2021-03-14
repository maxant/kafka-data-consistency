package ch.maxant.kdc.mf.contracts.boundary

import ch.maxant.kdc.mf.contracts.control.DefinitionService
import ch.maxant.kdc.mf.contracts.definitions.*
import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Timed
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import javax.inject.Inject
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response


@Path("/definitions")
@Tag(name = "definintions")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class DefinitionsResource(
    @Inject val definitionService: DefinitionService
) {

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
            APIResponse(description = "gets component definitions", responseCode = "200", content = [
                Content(mediaType = MediaType.APPLICATION_JSON, schema = Schema(implementation = ProductId::class))
            ])
    )
    @GET
    @Path("/components/{productId}")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun getComponents(@PathParam("productId") productId: ProductId): Response {
        val profile: Profile = Profiles.find()
        val product = Products.find(productId, profile.quantityMlOfProduct)
        val pack = Packagings.pack(profile.quantityOfProducts, product)
        val marketingDefaults = MarketingDefinitions.getDefaults(profile, product.productId)
        val mergedDefinitions = definitionService.getMergedDefinitions(pack, marketingDefaults)
        return Response.ok(mergedDefinitions).build()
    }

}

