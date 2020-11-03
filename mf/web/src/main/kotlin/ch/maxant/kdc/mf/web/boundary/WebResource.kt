package ch.maxant.kdc.mf.web.boundary

import ch.maxant.kdc.mf.library.ErrorsHandled
import com.fasterxml.jackson.databind.ObjectMapper
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.subscription.MultiEmitter
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import org.eclipse.microprofile.reactive.messaging.Acknowledgment
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.jboss.logging.Logger
import org.jboss.resteasy.annotations.SseElementType
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.ws.rs.*
import javax.ws.rs.core.MediaType


@ApplicationScoped
@Path("/web")
@Tag(name = "web")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class WebResource {

    @Inject
    lateinit var om: ObjectMapper

    @Inject
    lateinit var log: Logger

    // TODO tidy the entries up when they are no longer in use!
    val subscriptions: HashMap<String, MultiEmitter<in String?>> = HashMap()

    fun sendToSubscribers(requestId: String, event: String) {
        subscriptions
                .entries
                .filter { it.key == requestId }
                .filter { !it.value.isCancelled }
                .forEach {
                    log.info("emitting request $requestId")
                    it.value.emit(event)
                }
    }

    @Incoming("event-bus-in")
    @ErrorsHandled
    fun processEventBus(event: String) {
        process(event)
    }

    @Incoming("cases-in")
    @ErrorsHandled
    fun processCases(event: String) {
        process(event)
    }

    @Incoming("errors-in")
    @ErrorsHandled(sendToDlt = false)
    fun processErrors(event: String) {
        process(event)
    }

    private fun process(event: String) {
        val root = om.readTree(event)
        val requestId = root.get("requestId").asText()
        log.info("handling request $requestId")
        sendToSubscribers(requestId, event)
    }

    @GET
    @Path("/stream/{requestId}")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @SseElementType(MediaType.APPLICATION_JSON)
    fun stream(@PathParam("requestId") requestId: String): Multi<String?>? =
        Multi.createFrom()
                .emitter { e: MultiEmitter<in String?> ->
                    subscriptions[requestId] = e
                    e.onTermination {
                        e.complete()
                        subscriptions.remove(requestId)
                    }
                } // TODO if we get memory problems, add a different BackPressureStrategy as a second parameter to the emitter method

}

