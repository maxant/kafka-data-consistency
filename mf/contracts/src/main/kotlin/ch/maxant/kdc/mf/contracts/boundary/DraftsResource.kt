package ch.maxant.kdc.mf.contracts.boundary

import ch.maxant.kdc.mf.contracts.adapter.ESAdapter
import ch.maxant.kdc.mf.contracts.control.*
import ch.maxant.kdc.mf.contracts.definitions.*
import ch.maxant.kdc.mf.contracts.dto.*
import ch.maxant.kdc.mf.contracts.entity.ComponentEntity
import ch.maxant.kdc.mf.contracts.entity.ContractEntity
import ch.maxant.kdc.mf.contracts.entity.ContractState
import ch.maxant.kdc.mf.library.Context
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
import java.math.BigDecimal
import java.net.URI
import java.time.LocalDateTime
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
        var context: Context,

        @Inject
        var esAdapter: ESAdapter,

        @Inject
        var definitionService: DefinitionService,

        @Inject
        var instantiationService: InstantiationService
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
    @Transactional
    @Timed(unit = MetricUnits.MILLISECONDS)
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

        val contract = ContractEntity(draftRequest.contractId, start, end, context.user, profile.id)
        em.persist(contract)
        log.info("added contract ${contract.id} in state ${contract.contractState}")

        // get the product and package definitions
        val product = Products.find(draftRequest.productId, profile.quantityMlOfProduct)
        val pack = Packagings.pack(profile.quantityOfProducts, product)

        // get the defaults from marketing
        val marketingDefaults = MarketingDefinitions.getDefaults(profile, product.productId)

        val mergedDefinitions = definitionService.getMergedDefinitions(pack, marketingDefaults)

        val components = instantiationService.instantiate(mergedDefinitions)

        instantiationService.validate(mergedDefinitions, components)

        componentsRepo.saveInitialDraft(contract.id, components)
        log.info("packaged and persisted ${contract.id}")

        val draft = Draft(contract, components)

        esAdapter.createDraft(draft, draftRequest.partnerId)

        // it's ok to publish this model, because it's no different than getting pricing to
        // go fetch all this data, or us giving it to them. the dependency exists and is tightly
        // coupled. at least we don't need to know anything about pricing here! and passing it to them is
        // more efficient than them coming to read it afterwards
        eventBus.publish(draft)

        eventBus.publish(CreateCaseCommand(contract.id))

        if(draftRequest.partnerId != null) {
            eventBus.publish(
                    CreatePartnerRelationshipCommand(
                            draftRequest.partnerId!!,
                            contract.id,
                            CreatePartnerRelationshipCommand.Role.CONTRACT_HOLDER,
                            start,
                            end,
                            listOf(CreatePartnerRelationshipCommand.Role.SALES_REP)
                    )
            )
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
    @Path("/{contractId}/{componentId}/update-config/{param}/{newValue}")
    @Transactional
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun updateConfig(
            @PathParam("contractId") @Parameter(name = "contractId", required = true) contractId: UUID,
            @PathParam("componentId") @Parameter(name = "componentId", required = true) componentId: UUID,
            @PathParam("param") @Parameter(name = "param", required = true) param: String,
            @PathParam("newValue") @Parameter(name = "newValue", required = true) newValue: String
    ): Response = doByHandlingValidationExceptions {

        log.info("updating draft $contractId, setting value $newValue on parameter $param on component $componentId")

        val contract = getContractRequireDraftStateAndSetSyncTimestamp(contractId)

        val allComponents = instantiationService.reinstantiate(
            componentsRepo.updateConfig(contractId, componentId, ConfigurableParameter.valueOf(param), newValue)
        )

        val mergedDefinitions = getMergedDefinitionTree(allComponents, contract.profileId)

        instantiationService.validate(mergedDefinitions, allComponents)

        context.throwExceptionInContractsIfRequiredForDemo()

        esAdapter.updateComponents(contractId, allComponents)

        // instead of publishing the initial model based on definitions, which contain extra
        // info like possible inputs, we publish a simpler model here
        eventBus.publish(UpdatedDraft(contract, allComponents))

        Response.ok()
                .entity(contract)
                .build()
    }

    @Operation(summary = "increase cardinality", description = "descr")
    @APIResponses(
            APIResponse(description = "let's the user add the given path to the given component", responseCode = "200", content = [
                Content(mediaType = MediaType.APPLICATION_JSON, schema = Schema(implementation = ContractEntity::class))
            ])
    )
    @PUT
    @Path("/{contractId}/{parentComponentId}/increase-cardinality")
    @Transactional
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun increaseCardinality(
        @PathParam("contractId") @Parameter(name = "contractId", required = true) contractId: UUID,
        @PathParam("parentComponentId") @Parameter(name = "parentComponentId", required = true) parentComponentId: UUID,
        pathToAddString: String
    ): Response = doByHandlingValidationExceptions {

        // json body puts the string in quotes, so lets remove them
        val pathToAdd = pathToAddString.substring(1, pathToAddString.length - 1).replace("\\\\d*", "")

        log.info("increasing cardinality on draft $contractId, adding path $pathToAdd to component $parentComponentId")

        val contract = getContractRequireDraftStateAndSetSyncTimestamp(contractId)

        val allComponents = instantiationService.reinstantiate(
            ComponentEntity.Queries.selectByContractId(em, contractId)
        ).toMutableList()

        val mergedDefinitions = getMergedDefinitionTree(allComponents, contract.profileId)

        val definitionSubtreeToAdd = mergedDefinitions.find(pathToAdd)
        require(definitionSubtreeToAdd != null) { "No subtree found at $pathToAdd" }

        val additionalComponents = instantiationService.instantiateSubtree(allComponents, definitionSubtreeToAdd, parentComponentId)
        allComponents.addAll(additionalComponents)

        componentsRepo.addComponents(contractId, additionalComponents)

        instantiationService.validate(mergedDefinitions, allComponents)

        context.throwExceptionInContractsIfRequiredForDemo()

        esAdapter.updateComponents(contractId, allComponents)

        // instead of publishing the initial model based on definitions, which contain extra
        // info like possible inputs, we publish a simpler model here
        eventBus.publish(UpdatedDraft(contract, allComponents))

        Response.ok()
                .entity(contract)
                .build()
    }

    @Operation(summary = "decrease cardinality", description = "descr")
    @APIResponses(
            APIResponse(description = "let's the user remove the given component", responseCode = "200", content = [
                Content(mediaType = MediaType.APPLICATION_JSON, schema = Schema(implementation = ContractEntity::class))
            ])
    )
    @DELETE
    @Path("/{contractId}/{componentId}")
    @Transactional
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun decreaseCardinality(
        @PathParam("contractId") @Parameter(name = "contractId", required = true) contractId: UUID,
        @PathParam("componentId") @Parameter(name = "componentId", required = true) componentId: UUID
    ): Response = doByHandlingValidationExceptions {

        log.info("decreasing cardinality on draft $contractId, removing component $componentId")

        val contract = getContractRequireDraftStateAndSetSyncTimestamp(contractId)

        val allComponents = instantiationService.reinstantiate(
            ComponentEntity.Queries.selectByContractId(em, contractId)
        ).toMutableList()

        val toRemove = em.find(ComponentEntity::class.java, componentId)
        em.remove(toRemove)
        require(allComponents.removeIf { it.id == componentId }) { "unable to locate component to remove, from reinstantiated list $componentId" }

        val mergedDefinitions = getMergedDefinitionTree(allComponents, contract.profileId)

        instantiationService.validate(mergedDefinitions, allComponents)

        context.throwExceptionInContractsIfRequiredForDemo()

        esAdapter.updateComponents(contractId, allComponents)

        // instead of publishing the initial model based on definitions, which contain extra
        // info like possible inputs, we publish a simpler model here
        eventBus.publish(UpdatedDraft(contract, allComponents))

        Response.ok()
                .entity(contract)
                .build()
    }

    @Operation(summary = "Add discount")
    @APIResponses(
            APIResponse(description = "let's the user set a discount", responseCode = "200", content = [
                Content(mediaType = MediaType.APPLICATION_JSON, schema = Schema(implementation = ContractEntity::class))
            ])
    )
    @PUT
    @Path("/{contractId}/{componentId}/set-discount/{value}/")
    @Transactional
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun setDiscount(
            @PathParam("contractId") @Parameter(name = "contractId", required = true) contractId: UUID,
            @PathParam("componentId") @Parameter(name = "componentId", required = true) componentId: UUID,
            @PathParam("value") @Parameter(name = "value", required = true) value: String
    ): Response = doByHandlingValidationExceptions {

        log.info("setting discount on $contractId with value $value on component $componentId")

        val contract = getContractRequireDraftStateAndSetSyncTimestamp(contractId)

        val allComponents = instantiationService.reinstantiate(ComponentEntity.Queries.selectByContractId(em, contractId))

        context.throwExceptionInContractsIfRequiredForDemo()

        // instead of publishing the initial model based on definitions, which contain extra
        // info like possible inputs, we publish a simpler model here
        eventBus.publish(SetDiscountCommand(contract, allComponents, componentId, BigDecimal(value).abs().negate()))

        Response.ok()
                .entity(contract)
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
    @Transactional
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun offerDraft(
            @PathParam("contractId") @Parameter(name = "contractId", required = true) contractId: UUID
    ): Response = doByHandlingValidationExceptions {
        log.info("offering draft $contractId")

        // check draft status
        val contract = em.find(ContractEntity::class.java, contractId)
        require(contract.contractState == ContractState.DRAFT) { "contract is in wrong state: ${contract.contractState} - must be DRAFT" }
        // check all downstream services are in sync, in case there were any errors
        validationService.validateContractIsInSyncToOfferIt(contractId, contract.syncTimestamp)
        log.info("draft is valid")

        // TODO should this be done async? us emutiny to get free context propagation?

        contract.contractState = ContractState.OFFERED
        contract.offeredAt = LocalDateTime.now()
        contract.offeredBy = context.user

        // no need to update the sync timestamp, because otherwise we'd have to update it everywhere,
        // but we just validated that everything is indeed synchronised with us

        log.info("publishing OfferedDraft event")
        eventBus.publish(OfferedDraft(contract))

        esAdapter.updateState(contractId, contract.contractState)

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
    @Transactional
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun resyncDscAndPricing(
        @PathParam("contractId") @Parameter(name = "contractId", required = true) contractId: UUID
    ): Response = doByHandlingValidationExceptions {
        log.info("resyncing draft $contractId")

        // check draft status
        val contract = em.find(ContractEntity::class.java, contractId)
        require(contract.contractState == ContractState.DRAFT) { "contract is in wrong state: ${contract.contractState} - must be DRAFT" }

        val allComponents = instantiationService.reinstantiate(ComponentEntity.Queries.selectByContractId(em, contractId))

        eventBus.publish(UpdatedDraft(contract, allComponents))

        Response.accepted()
            .entity(contract)
            .build()
    }

    private fun getContractRequireDraftStateAndSetSyncTimestamp(contractId: UUID): ContractEntity {
        // check draft status
        val contract = em.find(ContractEntity::class.java, contractId)
        require(contract.contractState == ContractState.DRAFT) { "contract is in wrong state: ${contract.contractState} - must be DRAFT" }
        contract.syncTimestamp = System.currentTimeMillis()
        return contract
    }

    /** @return a tree of merged definitions for the given components */
    private fun getMergedDefinitionTree(
        allComponents: List<Component>,
        profileId: ProfileId
    ): MergedComponentDefinition {
        // recreate definitions. the config values will be all wrong, but that doesn't matter, because the validation
        // below is based on the instances configs and not this definition's configs.
        // IMPORTANT - we need to set the quantity to be the same as the actual product instance, so that any new
        // components are initially configured correctly, since the recipe is based on the initial quantity
        val productId = allComponents.find { it.productId != null }!!.productId!!
        val quantityMl = allComponents
            .find { it.productId != null }!! // the master quantity is configured on the product, which contains the productId
            .configs
            .find { it.name == ConfigurableParameter.VOLUME }!!
            .value as Int
        val product = Products.find(productId, quantityMl)
        val marketingDefaults = MarketingDefinitions.getDefaults(Profiles.get(profileId), product.productId)
        val pack = Packagings.find(allComponents.map { it.componentDefinitionId }, product)
        return definitionService.getMergedDefinitions(pack, marketingDefaults)
    }
}

