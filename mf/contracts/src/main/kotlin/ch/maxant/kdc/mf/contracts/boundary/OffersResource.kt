package ch.maxant.kdc.mf.contracts.boundary

import ch.maxant.kdc.mf.contracts.definitions.ContractDefinition
import ch.maxant.kdc.mf.contracts.definitions.Products
import ch.maxant.kdc.mf.contracts.definitions.Profile
import ch.maxant.kdc.mf.contracts.definitions.Profiles
import ch.maxant.kdc.mf.contracts.dto.Offer
import ch.maxant.kdc.mf.contracts.dto.OfferRequest
import ch.maxant.kdc.mf.contracts.entity.ContractEntity
import ch.maxant.kdc.mf.contracts.entity.Status
import java.net.URI
import javax.inject.Inject
import javax.persistence.EntityManager
import javax.transaction.Transactional
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import java.util.*
import javax.validation.Valid
import javax.validation.ValidationException
import javax.validation.Validator


@Path("/offers")
@Tag(name = "offers")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class OffersResource(
    @Inject
    public var em: EntityManager
) {

    @Operation(summary = "Create an offer", description = "descr")
    @APIResponses(
        APIResponse(description = "an offer", responseCode = "201", content = [
            Content(mediaType = MediaType.APPLICATION_JSON, schema = Schema(implementation = Offer::class))
        ])
    )
    @POST
    @Transactional
    fun create(
            @Parameter(name = "offerRequest", required = true)
            @Valid
            offerRequest: OfferRequest): Response {

        try {
            val start = offerRequest.start.atStartOfDay()
            val contractDefinition = ContractDefinition.find(offerRequest.productId, start)
            val end = start.plusDays(contractDefinition.defaultDurationDays)

            val contract = ContractEntity(UUID.randomUUID(), start, end, Status.DRAFT)
            em.persist(contract)

            val profile: Profile = Profiles.find()

            val product = Products.find(offerRequest.productId, profile.quantityMl)

            // TODO persist the product
            // TODO ensure product json is good - prolly ok here - but serialise it nicely for db

            return Response.created(URI.create("/${contract.id}")).entity(Offer(contract, product)).build()
        } catch (e: ValidationException) {
            // ResteasyViolationExceptionMapper doesnt handle ValidationException, and we don't want to create
            // some RestEasy*Impl Exception, so lets keep it simple, and do a mapping here like this:
            return Response.status(400).entity("""{"class": "${e.javaClass}", "error": "${e.message}"}""").build()
        }
    }

}