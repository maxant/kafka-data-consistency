package ch.maxant.kdc.mf.infra.boundary

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

    val log: Logger = Logger.getLogger(this.javaClass)

    // TODO tidy these up when they are no longer in use!
    val subscriptions: HashMap<String, MultiEmitter<in String?>> = HashMap()

    @Incoming("event-bus-in")
    fun process(event: String) {
        try {
            val root = om.readTree(event)
            val model = root.get("value").toString()

            val sendToSubscribers: (String) -> Unit = {
                val id = it
                subscriptions.entries.forEach {
                    if(it.key == id) {
                        if(!it.value.isCancelled) {
                            it.value.emit(model)
                        }
                    }
                }
            }

            when(root.get("event").asText()) {
                "OFFER_CREATED" -> sendToSubscribers(root.get("value").get("contract").get("id").asText())
                "UPDATED_PRICES" -> sendToSubscribers(root.get("contractId").asText())
            }
        } catch (e: Exception) {
            log.error("failed to process message $event", e) // TODO error handling / DLT
        }
    }

    @GET
    @Path("/stream/{subscribeToId}")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @SseElementType(MediaType.APPLICATION_JSON)
    fun stream(@PathParam("subscribeToId") subscribeToId: String): Multi<String?>? =
//        Broadcast
//        Multi.createFrom().e
        Multi.createFrom()
                .emitter { e: MultiEmitter<in String?> ->
                    subscriptions.put(subscribeToId, e)
                    e.onTermination {
                        e.complete()
                        subscriptions.remove(subscribeToId)
                    }
                } // TODO if we get memory problems, add a different BackPressureStrategy as a second parameter to the emitter method

}

