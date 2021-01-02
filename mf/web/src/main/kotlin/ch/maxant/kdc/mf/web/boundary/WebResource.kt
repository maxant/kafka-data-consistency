package ch.maxant.kdc.mf.web.boundary

import ch.maxant.kdc.mf.library.Context
import ch.maxant.kdc.mf.library.Context.Companion.DEMO_CONTEXT
import ch.maxant.kdc.mf.library.PimpedAndWithDltAndAck
import ch.maxant.kdc.mf.library.RequestId
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.subscription.MultiEmitter
import io.smallrye.reactive.messaging.kafka.IncomingKafkaRecordMetadata
import org.apache.kafka.common.header.Header
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
import javax.ws.rs.core.Response


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

//    @Incoming("event-bus-in")
    @PimpedAndWithDltAndAck
    fun processEventBus(message: Message<String>)= process(message)

    @Incoming("cases-in")
    @PimpedAndWithDltAndAck
    fun processCases(message: Message<String>) = process(message)

    @Incoming("partners-in")
    @PimpedAndWithDltAndAck
    fun processPartners(message: Message<String>) = process(message)

    @Incoming("errors-in")
    @PimpedAndWithDltAndAck
    fun processErrors(message: Message<String>) = process(message)

    private fun process(message: Message<String>): CompletionStage<*> {
        log.info("handling message for requestId ${context.requestId}")

        var headers = (message
                .getMetadata(IncomingKafkaRecordMetadata::class.java)
                .orElse(null)
                ?.headers?: emptyList<Header>())
                .toList()
                .filter { it.key() != DEMO_CONTEXT } // coz its a string of json that needs its quotes escaping and isnt useful to the web client, as it came from there
                .map { """ "${it.key()}": "${String(it.value())}" """ }
                .joinToString()
        headers = if(headers.isEmpty()) "" else "$headers,"


        val json = """{ $headers "payload": ${message.payload} }"""

        sendToSubscribers(context.requestId, json)
        return CompletableFuture.completedFuture(Unit)
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

    @GET
    @Path("/stats")
    fun stats() = Response.ok("""
        { "subscriptionsCount": ${this.subscriptions.size},
          "subscriptions": ${this.subscriptions.keys} 
        }""".trimIndent().replace(" ", "").replace("\r", "").replace("\n", "")).build()
}
