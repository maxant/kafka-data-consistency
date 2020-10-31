package ch.maxant.kdc.mf.infra.boundary

import ch.maxant.kdc.mf.library.ErrorHandler
import com.fasterxml.jackson.databind.ObjectMapper
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.subscription.MultiEmitter
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.jboss.logging.Logger
import org.jboss.resteasy.annotations.SseElementType
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.ws.rs.*
import javax.ws.rs.core.MediaType


@ApplicationScoped
@Path("/infra")
@Tag(name = "infra")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class InfraResource {

    @Inject
    lateinit var om: ObjectMapper

    @Inject
    lateinit var errorHandler: ErrorHandler

    val log: Logger = Logger.getLogger(this.javaClass)

    // TODO tidy these up when they are no longer in use!
    val subscriptions: HashMap<String, MultiEmitter<in String?>> = HashMap()

    fun sendToSubscribers(id: String, event: String) {
        subscriptions.entries.forEach {
            if(it.key == id) {
                if(!it.value.isCancelled) {
                    it.value.emit(event)
                }
            }
        }
    }

    @Incoming("event-bus-in")
    fun processEventBus(event: String) {
        var referenceId: String? = null
        try {
            val root = om.readTree(event)

            when(root.get("event").asText()) {
                "OFFER_CREATED" -> {
// TODO make lib extension method to get stuff out of ObjectNodes easier
                    referenceId = root.get("value").get("contract").get("id").asText()
                    sendToSubscribers(referenceId, event)
                }
                "UPDATED_PRICES" -> {
                    referenceId = root.get("contractId").asText()
                    sendToSubscribers(referenceId, event)
                }
            }
        } catch (e: Exception) {
            log.error("failed to process message $event - sending it to the DLT", e)
            errorHandler.dlt(referenceId, e, event)
        }
    }

    @Incoming("errors-in")
    fun processErrors(event: String) {
        try {
            val root = om.readTree(event)
            sendToSubscribers(root.get("referenceId").asText(), event)
        } catch (e: Exception) {
            log.error("DLT_FAILED: message $event", e) // TODO this is kinda terminal, coz we're already handling the DLT
        }
    }

    @Incoming("cases-in")
    fun processCases(event: String) {
        try {
            val root = om.readTree(event)
            sendToSubscribers(root.get("referenceId").asText(), event)
        } catch (e: Exception) {
            log.error("DLT_FAILED: message $event", e) // TODO this is kinda terminal, coz we're already handling the DLT
        }
    }

    @GET
    @Path("/stream/{subscribeToId}")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @SseElementType(MediaType.APPLICATION_JSON)
    fun stream(@PathParam("subscribeToId") subscribeToId: String): Multi<String?>? =
        Multi.createFrom()
                .emitter { e: MultiEmitter<in String?> ->
                    subscriptions.put(subscribeToId, e)
                    e.onTermination {
                        e.complete()
                        subscriptions.remove(subscribeToId)
                    }
                } // TODO if we get memory problems, add a different BackPressureStrategy as a second parameter to the emitter method

}

