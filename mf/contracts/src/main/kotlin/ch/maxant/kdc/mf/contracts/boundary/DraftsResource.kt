package ch.maxant.kdc.mf.contracts.boundary

import ch.maxant.kdc.mf.contracts.control.DraftsService
import ch.maxant.kdc.mf.contracts.dto.DraftRequest
import ch.maxant.kdc.mf.contracts.entity.ContractEntity
import ch.maxant.kdc.mf.library.Secure
import ch.maxant.kdc.mf.library.doByHandlingValidationExceptions
import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Timed
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import org.jboss.logging.Logger
import java.net.URI
import java.util.*
import javax.inject.Inject
import javax.transaction.TransactionManager
import javax.transaction.Transactional
import javax.validation.Valid
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("/drafts")
@Tag(name = "drafts")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class DraftsResource(
        @Inject val draftsService: DraftsService,
        @Inject val tm: TransactionManager
) {
    val log: Logger = Logger.getLogger(this.javaClass)

    @Operation(summary = "Create a draft", description = "descr")
    @APIResponses(
            APIResponse(description = "a draft", responseCode = "201", content = [
                Content(mediaType = MediaType.APPLICATION_JSON, schema = Schema(implementation = ContractEntity::class))
            ])
    )
    @POST
    @Secure
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun create(
            @Parameter(name = "draftRequest", required = true)
            @Valid
            draftRequest: DraftRequest): Response = doByHandlingValidationExceptions(tm) {

        val contract = draftsService.create(draftRequest)

        Response.created(URI.create("/${contract.id}"))
                .entity(contract)
                .build()
    }

    @Operation(summary = "Update draft configuration", description = "descr")
    @APIResponses(
            APIResponse(description = "let's the user update a part of the config", responseCode = "200", content = [
                Content(mediaType = MediaType.APPLICATION_JSON, schema = Schema(implementation = ContractEntity::class))
            ])
    )
    @PUT
    @Path("/{contractId}/update-config/{param}/{newValue}")
    @Timed(unit = MetricUnits.MILLISECONDS)
    @Transactional // TODO remove this
    fun updateConfig(
            @PathParam("contractId") @Parameter(name = "contractId", required = true) contractId: UUID,
            @PathParam("param") @Parameter(name = "param", required = true) param: String,
            @PathParam("newValue") @Parameter(name = "newValue", required = true) newValue: String,
            pathString: String
    ): Response = doByHandlingValidationExceptions(tm) {
        Response.ok()
                .entity(draftsService.updateConfig(contractId, param, newValue, pathString))
                .build()
    }

    @Operation(summary = "increase cardinality", description = "descr")
    @APIResponses(
            APIResponse(description = "let's the user add the given path to the given component", responseCode = "200", content = [
                Content(mediaType = MediaType.APPLICATION_JSON, schema = Schema(implementation = ContractEntity::class))
            ])
    )
    @PUT
    @Path("/{contractId}/increase-cardinality")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun increaseCardinality(
        @PathParam("contractId") @Parameter(name = "contractId", required = true) contractId: UUID,
        pathToAddString: String
    ): Response = doByHandlingValidationExceptions(tm) {
        Response.ok()
                .entity(draftsService.increaseCardinality(contractId, pathToAddString))
                .build()
    }

    @Operation(summary = "decrease cardinality", description = "descr")
    @APIResponses(
            APIResponse(description = "let's the user remove the given component", responseCode = "200", content = [
                Content(mediaType = MediaType.APPLICATION_JSON, schema = Schema(implementation = ContractEntity::class))
            ])
    )
    @DELETE
    @Path("/{contractId}")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun decreaseCardinality(
        @PathParam("contractId") @Parameter(name = "contractId", required = true) contractId: UUID,
        pathToRemoveString: String
    ): Response = doByHandlingValidationExceptions(tm) {
        Response.ok()
                .entity(draftsService.decreaseCardinality(contractId, pathToRemoveString))
                .build()
    }

    @Operation(summary = "Add discount")
    @APIResponses(
            APIResponse(description = "let's the user set a discount", responseCode = "200", content = [
                Content(mediaType = MediaType.APPLICATION_JSON, schema = Schema(implementation = ContractEntity::class))
            ])
    )
    @PUT
    @Path("/{contractId}/set-discount/{value}/")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun setDiscount(
            @PathParam("contractId") @Parameter(name = "contractId", required = true) contractId: UUID,
            @PathParam("value") @Parameter(name = "value", required = true) value: String,
            pathString: String
    ): Response = doByHandlingValidationExceptions(tm) {
        Response.ok()
                .entity(draftsService.setDiscount(contractId, value, pathString))
                .build()
    }

    @Operation(summary = "Offer draft", description = "offer a draft which has been configured to the customer needs, to them, in order for it to be accepted")
    @APIResponses(
            APIResponse(description = "offered draft", responseCode = "200", content = [
                Content(mediaType = MediaType.APPLICATION_JSON, schema = Schema(implementation = ContractEntity::class))
            ])
    )
    @PUT
    @Path("/{contractId}/offer")
    @Secure
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun offerDraft(
            @PathParam("contractId") @Parameter(name = "contractId", required = true) contractId: UUID
    ): Response = doByHandlingValidationExceptions(tm) {
        val contract = draftsService.offerDraft(contractId)
        Response.created(URI.create("/${contract.id}"))
                .entity(contract)
                .build()
    }

    @Operation(summary = "Resync draft", description = "if a draft is in an inconsistent state because DSC or pricing isnt up to date, this method will force recalculation and the caller should then update their model based on the resulting events")
    @APIResponses(
        APIResponse(responseCode = "202", content = [
            Content(mediaType = MediaType.APPLICATION_JSON, schema = Schema(implementation = ContractEntity::class))
        ])
    )
    @PUT
    @Path("/{contractId}/resync")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun resyncDscAndPricing(
        @PathParam("contractId") @Parameter(name = "contractId", required = true) contractId: UUID
    ): Response = doByHandlingValidationExceptions(tm) {
        val contract = draftsService.resyncDscAndPricing(contractId)
        Response.accepted()
            .entity(contract)
            .build()
    }
}

