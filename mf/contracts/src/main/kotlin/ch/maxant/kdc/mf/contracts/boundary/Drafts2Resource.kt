package ch.maxant.kdc.mf.contracts.boundary

import ch.maxant.kdc.mf.contracts.control.DraftsService
import ch.maxant.kdc.mf.contracts.control.EventBus
import ch.maxant.kdc.mf.contracts.dto.*
import ch.maxant.kdc.mf.contracts.entity.ComponentEntity
import ch.maxant.kdc.mf.contracts.entity.ContractEntity
import ch.maxant.kdc.mf.library.doByHandlingValidationExceptions
import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Timed
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import org.jboss.logging.Logger
import javax.enterprise.context.RequestScoped
import javax.inject.Inject
import javax.transaction.TransactionManager
import javax.ws.rs.Consumes
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("/drafts2")
@Tag(name = "drafts2")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class Drafts2Resource(
    @Inject val draftsService: DraftsService,
    @Inject val draftStateForNonPersistence: DraftStateForNonPersistence,
    @Inject val eventBus: EventBus,
    @Inject val tm: TransactionManager
) {
    val log: Logger = Logger.getLogger(this.javaClass)

    @Operation(summary = "execute some user actions", description = "descr")
    @APIResponses(
            APIResponse(description = "a draft", responseCode = "200", content = [
                Content(mediaType = MediaType.APPLICATION_JSON, schema = Schema(implementation = ContractEntity::class))
            ])
    )
    @POST
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun actions(
        contractActions: List<ContractActions>
    ): Response = doByHandlingValidationExceptions(tm) {
        draftStateForNonPersistence.replaying = true
        val contracts = mutableListOf<ContractWithWarnings>()
        for(contractAction in contractActions) {
            val warnings = mutableListOf<String?>()
            draftStateForNonPersistence.persist = contractAction.persist || contractAction.createOffer
            var contract = draftsService.create(contractAction.draftRequest)
            for(userAction in contractAction.userActions) {
                val path = userAction.params["path"] ?: userAction.params["pathToAdd"] ?: userAction.params["pathToRemove"]
                try {
                    when(userAction.action) {
                        Action.UPDATE_CONFIG -> draftsService.updateConfig(contract.id,
                                                                            userAction.params["param"]!!,
                                                                            userAction.params["newValue"]!!,
                                                                            userAction.params["path"]!!)
                        Action.INCREASE_CARDINALITY -> draftsService.increaseCardinality(contract.id,
                                                                            userAction.params["pathToAdd"]!!)
                        Action.DECREASE_CARDINALITY -> draftsService.decreaseCardinality(contract.id,
                                                                            userAction.params["pathToRemove"]!!)
                        Action.SET_DISCOUNT -> draftsService.setDiscount(contract.id,
                                                                            userAction.params["value"]!!,
                                                                            userAction.params["path"]!!)
                    }
                } catch(e: Exception) {
                    warnings.add("Failed to execute ${userAction.action} on path $path. ${e.message}")
                }
            }
            consolidateAndSendEvents(draftStateForNonPersistence.messages)
            if(contractAction.createOffer) {
                // NASTY, really we should wait for a pricing event before doing this, especially because it
                // might want to throw validation errors. we have to do this, because we validate that all microservices
                // have the same syncTimestamp
                Thread.sleep(1000)
                contract = draftsService.offerDraft(contract.id)
            }
            contracts.add(ContractWithWarnings(contract, warnings))
            draftStateForNonPersistence.reset()
        }

        Response.ok()
                .entity(contracts)
                .build()
    }

    private fun consolidateAndSendEvents(messages: List<Any>) {
        lateinit var lastDraft: Draft
        val setDiscountCommands = mutableListOf<SetDiscountCommand>()
        for (message in messages) {
            when(message) {
                is Draft -> lastDraft = message
                is SetDiscountCommand -> setDiscountCommands.add(message)
                is CreateCaseCommand -> Unit // wait til its persisted
            }
        }

        // even if a SetDiscountCommand came in after the last draft, it would contain the same contract and component
        // state as the last draft. if we just send the last message, we need to potentially send multiple manual
        // discounts/surcharges/conditions to DSC. we could... but i was lazy and just added that to the draft message.

        draftStateForNonPersistence.replaying = false // so that the event is actually sent!
        eventBus.publish(Draft(lastDraft.contract, lastDraft.allComponents, draftStateForNonPersistence.persist,
            setDiscountCommands.map { ManualDiscountSurcharge(it.componentId, it.value) }))
    }
}

data class ContractActions(
    val draftRequest: DraftRequest,
    val userActions: List<UserAction>,
    val persist: Boolean = false,
    val createOffer: Boolean = false
)
data class UserAction(
    val action: Action,
    val params: Map<String, String>

)
enum class Action {
    UPDATE_CONFIG, // {path}, {param}, {newValue}
    INCREASE_CARDINALITY,
    DECREASE_CARDINALITY,
    SET_DISCOUNT
}

data class ContractWithWarnings(
    val contract: ContractEntity,
    val warnings: List<String?>
)

@RequestScoped
class DraftStateForNonPersistence {

    var replaying = false
    var persist = true
    var initialised = false
    lateinit var contract: ContractEntity
    var components = mutableListOf<ComponentEntity>()
    var messages = mutableListOf<Any>()

    fun initialise(contract: ContractEntity) {
        this.contract = contract
        this.components.clear()
        this.messages.clear()
        this.persist = false
        this.initialised = true
    }

    fun addComponent(e: ComponentEntity) {
        this.components.add(e)
    }

    fun addMessage(message: Any) {
        this.messages.add(message)
    }

    fun reset() {
        this.persist = true
        this.initialised = false
        this.components.clear()
        this.messages.clear()
    }
}