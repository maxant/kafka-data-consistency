package ch.maxant.kdc.mf.web.boundary

import ch.maxant.kdc.mf.library.*
import com.fasterxml.jackson.databind.ObjectMapper
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.subscription.MultiEmitter
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.eclipse.microprofile.reactive.messaging.Message
import org.jboss.logging.Logger
import org.jboss.resteasy.annotations.SseElementType
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
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

    val log: Logger = Logger.getLogger(this.javaClass)

    @Inject
    lateinit var context: Context

    // TODO tidy the entries up when they are no longer in use!
    val subscriptions: HashMap<String, MultiEmitter<in String?>> = HashMap()

    fun sendToSubscribers(requestId: RequestId, json: String) {
        subscriptions
                .entries
                .filter { it.key == requestId.toString() }
                .filter { !it.value.isCancelled }
                .forEach {
                    log.info("emitting request $requestId")
                    it.value.emit(json)
                }
    }

    @Incoming("event-bus-in")
    @PimpedAndWithDltAndAck
    fun processEventBus(message: Message<String>)= process(message)

    @Incoming("cases-in")
    @PimpedAndWithDltAndAck
    fun processCases(message: Message<String>) = process(message)

    @Incoming("errors-in")
    @PimpedAndWithDltAndAck
    fun processErrors(message: Message<String>) = process(message)

    private fun process(message: Message<String>): CompletionStage<*> {
        log.info("handling message ${context.requestId}")

        val json = """
            { 
              "event": "${context.event}",
              "payload": ${message.payload}
            }
        """.trimIndent()

        sendToSubscribers(context.requestId, json)
        return CompletableFuture.completedFuture(Unit)
    }

    @GET
    @Path("/stream/{$REQUEST_ID}")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @SseElementType(MediaType.APPLICATION_JSON)
    fun stream(@PathParam(REQUEST_ID) requestId: String): Multi<String?>? =
        Multi.createFrom()
                .emitter { e: MultiEmitter<in String?> ->
                    subscriptions[requestId] = e
                    e.onTermination {
                        e.complete()
                        subscriptions.remove(requestId)
                    }
                } // TODO if we get memory problems, add a different BackPressureStrategy as a second parameter to the emitter method

}
