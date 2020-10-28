package ch.maxant.kdc.mf.contracts.boundary

import ch.maxant.kdc.mf.contracts.control.ComponentsRepo
import ch.maxant.kdc.mf.contracts.control.EventBus
import ch.maxant.kdc.mf.contracts.control.OfferEvent
import ch.maxant.kdc.mf.contracts.definitions.*
import ch.maxant.kdc.mf.contracts.dto.Offer
import ch.maxant.kdc.mf.contracts.dto.OfferRequest
import ch.maxant.kdc.mf.contracts.entity.ContractEntity
import ch.maxant.kdc.mf.contracts.entity.Status
import ch.maxant.kdc.mf.library.doByHandlingValidationExceptions
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import java.net.URI
import java.util.*
import javax.inject.Inject
import javax.persistence.EntityManager
import javax.transaction.Transactional
import javax.validation.Valid
import javax.ws.rs.Consumes
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response


@Path("/offers")
@Tag(name = "offers")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class OffersResource(
    @Inject
    public var em: EntityManager, // TODO public in an attempt to avoid quarkus warning during startup

    @Inject
    var componentsRepo: ComponentsRepo,

    @Inject
    var eventBus: EventBus
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
            offerRequest: OfferRequest): Response = doByHandlingValidationExceptions {

        val profile: Profile = Profiles.find()

        val start = offerRequest.start.atStartOfDay()
        val contractDefinition = ContractDefinition.find(offerRequest.productId, start)
        val end = start.plusDays(contractDefinition.defaultDurationDays)

        val contract = ContractEntity(offerRequest.contractId, start, end, Status.DRAFT)
        em.persist(contract)

        val product = Products.find(offerRequest.productId, profile.quantityMlOfProduct)
        val pack = Packagings.pack(profile.quantityOfProducts, product)
        componentsRepo.saveInitialOffer(contract.id, pack)

        val offer = Offer(contract, pack)

        // it's ok to publish this model, because it's no different than getting pricing to
        // go fetch all this data, or us giving it to them. the dependency exists and is tightly
        // coupled. at least we don't need to know anything about pricing here!
        eventBus.publish(OfferEvent(offer))

        Response
                .created(URI.create("/${contract.id}"))
                .entity(offer)
                .build()
    }
}