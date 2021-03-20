package ch.maxant.kdc.mf.contracts.control

import ch.maxant.kdc.mf.contracts.adapter.ESAdapter
import ch.maxant.kdc.mf.contracts.boundary.DraftStateForNonPersistence
import ch.maxant.kdc.mf.contracts.boundary.PersistenceTypes
import ch.maxant.kdc.mf.contracts.definitions.*
import ch.maxant.kdc.mf.contracts.dto.*
import ch.maxant.kdc.mf.contracts.entity.ComponentEntity
import ch.maxant.kdc.mf.contracts.entity.ContractEntity
import ch.maxant.kdc.mf.contracts.entity.ContractState
import ch.maxant.kdc.mf.library.Context
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.quarkus.redis.client.RedisClient
import org.jboss.logging.Logger
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.persistence.EntityManager
import javax.transaction.Transactional

@ApplicationScoped
@Transactional
class DraftsService(
        // TODO address quarkus warning during startup
        @Inject val em: EntityManager,
        @Inject val componentsRepo: ComponentsRepo,
        @Inject val eventBus: EventBus,
        @Inject val validationService: ValidationService,
        @Inject val context: Context,
        @Inject val esAdapter: ESAdapter,
        @Inject val  definitionService: DefinitionService,
        @Inject val instantiationService: InstantiationService,
        @Inject val draftStateForNonPersistence: DraftStateForNonPersistence,
        @Inject val redis: RedisClient,
        @Inject val om: ObjectMapper
) {
    val log: Logger = Logger.getLogger(this.javaClass)

    fun create(draftRequest: DraftRequest): ContractEntity = withRedisRollback(redis, draftRequest.contractId) {
        log.info("creating draft $draftRequest")

        context.throwExceptionInContractsIfRequiredForDemo()

        val profile: Profile = Profiles.find()
        log.info("using profile ${profile.id}")

        val start = draftRequest.start.atStartOfDay()
        val contractDefinition = ContractDefinition.find(draftRequest.productId, start)
        val end = start.plusDays(contractDefinition.defaultDurationDays)

        val contract = ContractEntity(draftRequest.contractId, start, end, context.user, profile.id)
        when(draftStateForNonPersistence.persist) {
            PersistenceTypes.DB -> em.persist(contract)
            PersistenceTypes.IN_MEMORY -> draftStateForNonPersistence.initialise(contract)
            PersistenceTypes.REDIS -> redis.set(listOf("${contract.id}-contract", om.writeValueAsString(contract)))
        }
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
        log.info("packaged ${contract.id}")

        val draft = Draft(contract, components, draftStateForNonPersistence.persist)

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
        contract
    }

    fun updateConfig(
            contractId: UUID,
            param: String,
            newValue: String,
            pathString: String
    ) = withRedisRollback(redis, contractId) {
        val path = pathString.replace("\"", "") // json body puts the string in quotes, so lets remove them

        log.info("updating draft $contractId, setting value $newValue on parameter $param on component $path")

        val contract = getContractRequireDraftStateAndSetSyncTimestamp(contractId)

        val componentEntities =
            when(draftStateForNonPersistence.persist) {
                PersistenceTypes.DB -> ComponentEntity.Queries.selectByContractId(em, contractId)
                PersistenceTypes.IN_MEMORY -> draftStateForNonPersistence.components
                PersistenceTypes.REDIS -> {
                    om.readValue(redis.get("$contractId-components").toString())
                }
            }
        var allComponents = instantiationService.reinstantiate(componentEntities)

        val componentId = instantiationService.getComponentIdForPath(allComponents, path)

        allComponents = componentsRepo.updateConfig(componentEntities, componentId, ConfigurableParameter.valueOf(param), newValue)

        val mergedDefinitions = getMergedDefinitionTree(allComponents, contract.profileId)

        instantiationService.validate(mergedDefinitions, allComponents)

        context.throwExceptionInContractsIfRequiredForDemo()

        esAdapter.updateComponents(contractId, allComponents)

        // instead of publishing the initial model based on definitions, which contain extra
        // info like possible inputs, we publish a simpler model here
        eventBus.publish(Draft(contract, allComponents, draftStateForNonPersistence.persist))

        contract
    }

    fun increaseCardinality(
        contractId: UUID,
        pathToAddString: String
    ) = withRedisRollback(redis, contractId) {
        val pathToAdd = pathToAddString.replace("\"", "") // json body puts the string in quotes, so lets remove them

        log.info("increasing cardinality on draft $contractId, adding path $pathToAdd")

        val contract = getContractRequireDraftStateAndSetSyncTimestamp(contractId)

        val allComponents = instantiationService.reinstantiate(
            when(draftStateForNonPersistence.persist) {
                PersistenceTypes.DB -> ComponentEntity.Queries.selectByContractId(em, contractId)
                PersistenceTypes.IN_MEMORY -> draftStateForNonPersistence.components
                PersistenceTypes.REDIS -> om.readValue(redis.get("$contractId-components").toString())
            }
        ).toMutableList()

        val mergedDefinitions = getMergedDefinitionTree(allComponents, contract.profileId)

        val definitionSubtreeToAdd = mergedDefinitions.find(pathToAdd)
        require(definitionSubtreeToAdd != null) { "No subtree found at $pathToAdd" }

        val additionalComponents = instantiationService.instantiateSubtree(allComponents, definitionSubtreeToAdd, pathToAdd)

        componentsRepo.addComponents(contractId, additionalComponents, allComponents)

        allComponents.addAll(additionalComponents)

        instantiationService.validate(mergedDefinitions, allComponents)

        context.throwExceptionInContractsIfRequiredForDemo()

        esAdapter.updateComponents(contractId, allComponents)

        // instead of publishing the initial model based on definitions, which contain extra
        // info like possible inputs, we publish a simpler model here
        eventBus.publish(Draft(contract, allComponents, draftStateForNonPersistence.persist))

        contract
    }

    fun decreaseCardinality(
        contractId: UUID,
        pathToRemoveString: String
    ) = withRedisRollback(redis, contractId) {
        val pathToRemove = pathToRemoveString.replace("\"", "") // json body puts the string in quotes, so lets remove them

        log.info("decreasing cardinality on draft $contractId, removing $pathToRemove")

        val contract = getContractRequireDraftStateAndSetSyncTimestamp(contractId)

        val allComponents = instantiationService.reinstantiate(
            when(draftStateForNonPersistence.persist) {
                PersistenceTypes.DB -> ComponentEntity.Queries.selectByContractId(em, contractId)
                PersistenceTypes.IN_MEMORY -> draftStateForNonPersistence.components
                PersistenceTypes.REDIS -> om.readValue(redis.get("$contractId-components").toString())
            }
        ).toMutableList()

        val component = instantiationService.getComponentForPath(allComponents, pathToRemove)

        val toRemove:List<Component> = getAllComponentsFromHereDownwards(allComponents, component.id)

        toRemove.forEach {
            when(draftStateForNonPersistence.persist) {
                PersistenceTypes.DB -> {
                    val entityToRemove = em.find(ComponentEntity::class.java, it.id)
                    em.remove(entityToRemove)
                }
                PersistenceTypes.IN_MEMORY -> draftStateForNonPersistence.components.removeIf { c -> it.id == c.id }
                PersistenceTypes.REDIS -> Unit // handle them all together below
            }

            // remove from DTO modell too
            require(allComponents.removeIf { c -> c.id == it.id }) { "unable to locate component to remove, from reinstantiated list $it.id" }
        }
        if(draftStateForNonPersistence.persist == PersistenceTypes.REDIS) {
            val entities = om.readValue<List<ComponentEntity>>(redis.get("$contractId-components").toString()).toMutableList()
            entities.removeIf { toRemove.map { c -> c.id } .contains(it.id) }
            redis.set(listOf("$contractId-components", om.writeValueAsString(entities)))
        }

        val mergedDefinitions = getMergedDefinitionTree(allComponents, contract.profileId)

        instantiationService.validate(mergedDefinitions, allComponents)

        context.throwExceptionInContractsIfRequiredForDemo()

        esAdapter.updateComponents(contractId, allComponents)

        // instead of publishing the initial model based on definitions, which contain extra
        // info like possible inputs, we publish a simpler model here
        eventBus.publish(Draft(contract, allComponents, draftStateForNonPersistence.persist))

        contract
    }

    private fun getAllComponentsFromHereDownwards(components: List<Component>, componentId: UUID): List<Component> {
        val list = mutableListOf<Component>()
        list.addAll(components.filter { it.id == componentId })
        val childrenThereof = components.filter { componentId == it.parentId }
        if(childrenThereof.isNotEmpty()) {
            list.addAll(childrenThereof.flatMap { getAllComponentsFromHereDownwards(components, it.id) })
        }
        return list
    }

    fun setDiscount(
            contractId: UUID,
            value: String,
            pathString: String
    ) = withRedisRollback(redis, contractId) {
        val path = pathString.replace("\"", "") // json body puts the string in quotes, so lets remove them

        log.info("setting discount on $contractId with value $value on component $path")

        val contract = getContractRequireDraftStateAndSetSyncTimestamp(contractId)

        // this method does nothing here, except send the command down the line to DSC and Pricing

        val allComponents = instantiationService.reinstantiate(
            when(draftStateForNonPersistence.persist) {
                PersistenceTypes.DB -> ComponentEntity.Queries.selectByContractId(em, contractId)
                PersistenceTypes.IN_MEMORY -> draftStateForNonPersistence.components
                PersistenceTypes.REDIS -> om.readValue(redis.get("$contractId-components").toString())
            }
        )
        val componentId = instantiationService.getComponentIdForPath(allComponents, path)

        context.throwExceptionInContractsIfRequiredForDemo()

        // instead of publishing the initial model based on definitions, which contain extra
        // info like possible inputs, we publish a simpler model here
        eventBus.publish(SetDiscountCommand(contract, allComponents, componentId, BigDecimal(value).abs().negate(), draftStateForNonPersistence.persist))

        contract
    }

    fun offerDraft(
            contractId: UUID
    ): ContractEntity {
        log.info("offering draft $contractId")

        // check draft status
        val contract = em.find(ContractEntity::class.java, contractId)
        require(contract.contractState == ContractState.DRAFT) { "contract is in wrong state: ${contract.contractState} - must be DRAFT" }

        // validate components
        val allComponents = instantiationService.reinstantiate(
            ComponentEntity.Queries.selectByContractId(em, contractId)
        )
        val mergedDefinitions = getMergedDefinitionTree(allComponents, contract.profileId)
        instantiationService.validate(mergedDefinitions, allComponents)
        log.info("draft is valid from an internal perspective")

        // check all downstream services are in sync, in case there were any errors
        validationService.validateContractIsInSyncToOfferIt(contractId, contract.syncTimestamp)
        log.info("draft is valid from an external perspective")

        // update state
        contract.contractState = ContractState.OFFERED
        contract.offeredAt = LocalDateTime.now()
        contract.offeredBy = context.user

        // no need to update the sync timestamp, because otherwise we'd have to update it everywhere,
        // but we just validated that everything in other microservices is indeed synchronised with us

        log.info("publishing OfferedDraft event")
        eventBus.publish(OfferedDraft(contract))

        esAdapter.updateState(contractId, contract.contractState)

        return contract
    }

    fun resyncDscAndPricing(
        contractId: UUID
    ): ContractEntity {
        log.info("resyncing draft $contractId")

        var persistenceType = PersistenceTypes.DB
        val rr = redis.get("$contractId-contract")
        val contract = if(rr != null) {
            val c = om.readValue<ContractEntity>(rr.toString())
            persistenceType = PersistenceTypes.REDIS
            c
        } else {
            em.find(ContractEntity::class.java, contractId) ?: throw UnsupportedOperationException("persistence type IN_MEMORY not supported")
        }

        // check draft status
        require(contract.contractState == ContractState.DRAFT) { "contract is in wrong state: ${contract.contractState} - must be DRAFT" }

        val allComponents = instantiationService.reinstantiate(
            when(persistenceType) {
                PersistenceTypes.DB -> ComponentEntity.Queries.selectByContractId(em, contractId)
                PersistenceTypes.IN_MEMORY -> throw UnsupportedOperationException()
                PersistenceTypes.REDIS -> om.readValue(redis.get("$contractId-components").toString())
            }
        )
        eventBus.publish(Draft(contract, allComponents, persistenceType))

        return contract
    }

    private fun getContractRequireDraftStateAndSetSyncTimestamp(contractId: UUID): ContractEntity {
        // check draft status
        return when(draftStateForNonPersistence.persist) {
            PersistenceTypes.DB -> {
                val contract = em.find(ContractEntity::class.java, contractId)
                require(contract.contractState == ContractState.DRAFT) { "contract is in wrong state: ${contract.contractState} - must be DRAFT" }
                contract.syncTimestamp = System.currentTimeMillis()
                contract
            }
            PersistenceTypes.IN_MEMORY -> {
                val contract = draftStateForNonPersistence.contract
                contract.syncTimestamp = System.currentTimeMillis()
                contract
            }
            PersistenceTypes.REDIS -> {
                om.readValue(redis.get("$contractId-contract").toString())
            }
        }
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

private fun withRedisRollback(redis: RedisClient, contractId: UUID, fn: () -> ContractEntity): ContractEntity {
    val contract = redis.get("$contractId-contract")
    val components = redis.get("$contractId-components")
    return try {
        fn()
    } catch (e: Exception) {
        Logger.getLogger("RedisRollback").warn("rolling back redis for contract $contractId", e)
        if(contract != null) redis.set(listOf("$contractId-contract", contract.toString()))
        if(components != null) redis.set(listOf("$contractId-components", components.toString()))
        throw e
    }
}