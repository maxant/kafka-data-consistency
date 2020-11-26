package ch.maxant.kdc.mf.contracts.boundary

import ch.maxant.kdc.mf.contracts.control.ComponentsRepo
import ch.maxant.kdc.mf.contracts.control.EventBus
import ch.maxant.kdc.mf.contracts.control.ValidationService
import ch.maxant.kdc.mf.contracts.definitions.*
import ch.maxant.kdc.mf.contracts.dto.*
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
import java.util.*
import javax.inject.Inject
import javax.persistence.EntityManager
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
        // TODO address quarkus warning during startup
        @Inject
        var em: EntityManager,

        @Inject
        var componentsRepo: ComponentsRepo,

        @Inject
        var eventBus: EventBus,

        @Inject
        var validationService: ValidationService,

        @Inject
        var context: Context
) {

    val log: Logger = Logger.getLogger(this.javaClass)

    @Operation(summary = "Create a draft", description = "descr")
    @APIResponses(
            APIResponse(description = "a draft", responseCode = "201", content = [
                Content(mediaType = MediaType.APPLICATION_JSON, schema = Schema(implementation = ContractEntity::class))
            ])
    )
    @POST
    @Transactional
    fun create(
            @Parameter(name = "draftRequest", required = true)
            @Valid
            draftRequest: DraftRequest): Response = doByHandlingValidationExceptions {

        log.info("creating draft $draftRequest")

        context.throwExceptionInContractsIfRequiredForDemo()

        val profile: Profile = Profiles.find()
        log.info("using profile ${profile.id}")

        val start = draftRequest.start.atStartOfDay()
        val contractDefinition = ContractDefinition.find(draftRequest.productId, start)
        val end = start.plusDays(contractDefinition.defaultDurationDays)

        val contract = ContractEntity(draftRequest.contractId, start, end, ContractState.DRAFT, System.currentTimeMillis())
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

        eventBus.publish(CreateCaseCommand(contract.id))

        if(draftRequest.partnerId != null) {
            eventBus.publish(CreatePartnerRelationshipCommand(draftRequest.partnerId!!, contract.id, "CONTRACT_HOLDER"))
        }

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
    @Path("/{contractId}/{componentId}/{param}/{newValue}")
    @Transactional
    fun updateConfig(
            @PathParam("contractId") @Parameter(name = "contractId", required = true) contractId: UUID,
            @PathParam("componentId") @Parameter(name = "componentId", required = true) componentId: UUID,
            @PathParam("param") @Parameter(name = "param", required = true) param: String,
            @PathParam("newValue") @Parameter(name = "newValue", required = true) newValue: String
    ): Response = doByHandlingValidationExceptions {

        log.info("updating draft $contractId, setting value $newValue on parameter $param on component $componentId")

        // check draft status
        val contract = em.find(ContractEntity::class.java, contractId)
        require(contract.contractState == ContractState.DRAFT) { "contract is in wrong state: ${contract.contractState} - must be DRAFT" }
        contract.syncTimestamp = System.currentTimeMillis()

        val allComponents = componentsRepo.updateConfig(contractId, componentId, ConfigurableParameter.valueOf(param), newValue)

        context.throwExceptionInContractsIfRequiredForDemo()

        // instead of publishing the initial model based on definitions, which contain extra
        // info like possible inputs, we publish a simpler model here
        eventBus.publish(UpdatedDraft(contract, allComponents))

        Response.created(URI.create("/${contract.id}"))
                .entity(contract)
                .build()
    }

    @Operation(summary = "Offer draft", description = "offer a draft which has been configured to the customer needs, to them, in order for it to be accepted")
    @APIResponses(
            APIResponse(description = "let's the user update a part of the config", responseCode = "200", content = [
                Content(mediaType = MediaType.APPLICATION_JSON, schema = Schema(implementation = ContractEntity::class))
            ])
    )
    @PUT
    @Path("/{contractId}/offer")
    @Transactional
    fun offerDraft(
            @PathParam("contractId") @Parameter(name = "contractId", required = true) contractId: UUID
    ): Response = doByHandlingValidationExceptions {

        log.info("offering draft $contractId")

        // check draft status
        val contract = em.find(ContractEntity::class.java, contractId)
        require(contract.contractState == ContractState.DRAFT) { "contract is in wrong state: ${contract.contractState} - must be DRAFT" }
        // check all downstream services are in sync, in case there were any errors
        validationService.validateContractIsInSync(contractId, contract.syncTimestamp)
        log.info("draft is valid")

        // TODO should this be done async? us emutiny to get free context propagation?

        contract.contractState = ContractState.OFFERED
        // no need to update the sync timestamp, because otherwise we'd have to update it everywhere,
        // but we just validated that everything is indeed synchronised with us

        log.info("publishing OfferedDraft event")
        eventBus.publish(OfferedDraft(contract))

        Response.created(URI.create("/${contract.id}"))
                .entity(contract)
                .build()
    }

}

