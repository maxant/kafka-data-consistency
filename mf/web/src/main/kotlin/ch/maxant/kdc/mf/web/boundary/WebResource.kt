package ch.maxant.kdc.mf.web.boundary

import ch.maxant.kdc.mf.library.Context
import ch.maxant.kdc.mf.library.Context.Companion.DEMO_CONTEXT
import ch.maxant.kdc.mf.library.PimpedAndWithDltAndAck
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.subscription.MultiEmitter
import io.smallrye.reactive.messaging.kafka.IncomingKafkaRecordMetadata
import org.apache.kafka.common.header.Header
import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Timed
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.eclipse.microprofile.reactive.messaging.Message
import org.jboss.logging.Logger
import org.jboss.resteasy.annotations.SseElementType
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.servlet.http.HttpServletResponse
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

    // TODO tidy the entries up when they are no longer in use! tip: see isCancelled below - altho theyre already removed with onterminate at the bottom?
    val subscriptions = ConcurrentHashMap<String, EmitterState>()

    fun sendToSubscribers(requestId: String, json: String) {

        val toRemove = mutableSetOf<String>()
        subscriptions
                .entries
                .filter { it.value.isExpiredOrCancelled() }
                .forEach {
                    synchronized(it.value.emitter) { // TODO is this necessary? does it hurt??
                        log.info("closing cancelled/expired emitter for request $requestId. cancelled: ${it.value.emitter.isCancelled}, expired: ${it.value.isExpired()}")
                        it.value.emitter.complete()
                        toRemove.add(it.key)
                    }
                }
        toRemove.forEach { subscriptions.remove(it) }

        subscriptions
                .entries
                .filter { it.key == requestId }
                .filter { !it.value.isExpiredOrCancelled() }
                .forEach {
                    synchronized(it.value) { // TODO is this necessary? does it hurt??
                        log.info("emitting request $requestId to subscriber ${it.key}: $json. context.requestId is ${context.getRequestIdSafely().requestId}")
                        it.value.touch()
                        it.value.emitter.emit(json)
                    }
                }
    }

    @Incoming("cases-in")
    @PimpedAndWithDltAndAck
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun processCases(message: Message<String>) = process(message)

    @Incoming("partners-in")
    @PimpedAndWithDltAndAck
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun processPartners(message: Message<String>) = process(message)

    @Incoming("errors-in")
    @PimpedAndWithDltAndAck
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun processErrors(message: Message<String>) = process(message)

    @Deprecated(message = "use native kafka record")
    private fun process(message: Message<String>): CompletionStage<*> {
        var headers = // coz its a string of json that needs its quotes escaping and isnt useful to the web client, as it came from there
                (message
                        .getMetadata(IncomingKafkaRecordMetadata::class.java)
                        .orElse(null)
                        ?.headers ?: emptyList<Header>())
                        .toList()

        val requestId = headers
                .filter { it.key() == Context.REQUEST_ID }
                .map { String(it.value()) }
                .firstOrNull()?:context.getRequestIdSafely().toString()

        log.info("handling message for requestId $requestId")

        var headers2 = headers
                        .filter { it.key() != DEMO_CONTEXT }
                        .joinToString { """ "${it.key()}": "${String(it.value())}" """ }

        headers2 = if(headers2.isEmpty()) "" else "$headers2,"

        val json = """{ $headers2 "payload": ${message.payload} }"""

        sendToSubscribers(requestId, json)

        return CompletableFuture.completedFuture(Unit)
    }

    @GET
    @Path("/stream/{requestId}")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @SseElementType(MediaType.APPLICATION_JSON)
    fun stream(@PathParam("requestId") requestId: String, @javax.ws.rs.core.Context response: HttpServletResponse): Multi<String> {
        // https://serverfault.com/questions/801628/for-server-sent-events-sse-what-nginx-proxy-configuration-is-appropriate
        response.setHeader("Cache-Control", "no-cache")
        response.setHeader("X-Accel-Buffering", "no")
        return Multi.createFrom()
                .emitter { e: MultiEmitter<in String?> ->
                    subscriptions[requestId] = EmitterState(e, System.currentTimeMillis())
                    e.onTermination {
                        e.complete()
                        log.info("removing termindated subscription ${requestId}")
                        subscriptions.remove(requestId)
                    }
                } // TODO if we get memory problems, add a different BackPressureStrategy as a second parameter to the emitter method
    }

    @GET
    @Path("/stats")
    fun stats() = Response.ok("""
        { "subscriptionsCount": ${this.subscriptions.size},
          "subscriptions": ${this.subscriptions.keys} 
        }""".trimIndent().replace(" ", "").replace("\r", "").replace("\n", "")).build()
}

data class EmitterState(val emitter: MultiEmitter<in String?>, var lastUsed: Long = System.currentTimeMillis()) {
    val FIVE_MINUTES = 5 * 60 * 1_000

    fun isExpired() = System.currentTimeMillis() - this.lastUsed > FIVE_MINUTES

    fun isExpiredOrCancelled() = this.emitter.isCancelled || isExpired()

    fun touch() {
        lastUsed = System.currentTimeMillis()
    }
}