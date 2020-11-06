package ch.maxant.kdc.mf.contracts.boundary

import ch.maxant.kdc.mf.contracts.control.ComponentsRepo
import ch.maxant.kdc.mf.contracts.control.CreateCaseCommand
import ch.maxant.kdc.mf.contracts.control.EventBus
import ch.maxant.kdc.mf.contracts.definitions.*
import ch.maxant.kdc.mf.contracts.dto.Draft
import ch.maxant.kdc.mf.contracts.dto.DraftRequest
import ch.maxant.kdc.mf.contracts.entity.ContractEntity
import ch.maxant.kdc.mf.contracts.entity.ContractState
import ch.maxant.kdc.mf.library.Context
import ch.maxant.kdc.mf.library.doByHandlingValidationExceptions
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import org.jboss.logging.Logger
import java.net.URI
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


@Path("/drafts")
@Tag(name = "drafts")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class DraftsResource(
    // TODO address quarkus warning during startup
    @Inject
    var em: EntityManager,

    @Inject
    var componentsRepo: ComponentsRepo,

    @Inject
    var eventBus: EventBus,

    @Inject
    var context: Context
) {

    val log: Logger = Logger.getLogger(this.javaClass)

    @Operation(summary = "Create a draft", description = "descr")
    @APIResponses(
        APIResponse(description = "a draft", responseCode = "201", content = [
            Content(mediaType = MediaType.APPLICATION_JSON, schema = Schema(implementation = Draft::class))
        ])
    )
    @POST
    @Transactional
    fun create(
            @Parameter(name = "draftRequest", required = true)
            @Valid
            draftRequest: DraftRequest): Response = doByHandlingValidationExceptions {

        log.info("creating draft $draftRequest for requestId ${context.requestId}")

        val profile: Profile = Profiles.find()
        log.info("using profile ${profile.id}")

        val start = draftRequest.start.atStartOfDay()
        val contractDefinition = ContractDefinition.find(draftRequest.productId, start)
        val end = start.plusDays(contractDefinition.defaultDurationDays)

        val contract = ContractEntity(draftRequest.contractId, start, end, ContractState.DRAFT)
        em.persist(contract)
        log.info("added contract ${contract.id} in state ${contract.contractState}")

        val product = Products.find(draftRequest.productId, profile.quantityMlOfProduct)
        val pack = Packagings.pack(profile.quantityOfProducts, product)
        componentsRepo.saveInitialDraft(contract.id, pack)
        log.info("packaged ${contract.id}")

        val draft = Draft(contract, pack)

        // it's ok to publish this model, because it's no different than getting pricing to
        // go fetch all this data, or us giving it to them. the dependency exists and is tightly
        // coupled. at least we don't need to know anything about pricing here! and passing it to them is
        // more efficient than them coming to read it afterwards
        eventBus.publish(draft)
        log.info("published ${contract.id}")

        eventBus.publish(CreateCaseCommand(contract.id))
        log.info("sent case command ${contract.id}")

        Response.created(URI.create("/${contract.id}"))
                .entity(draft)
                .build()
    }
}

