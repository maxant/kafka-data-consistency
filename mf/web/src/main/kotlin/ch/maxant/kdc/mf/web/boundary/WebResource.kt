package ch.maxant.kdc.mf.web.boundary

import ch.maxant.kdc.mf.library.Context
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.subscription.MultiEmitter
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import org.jboss.logging.Logger
import org.jboss.resteasy.annotations.SseElementType
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
                        log.info("removing termindated subscription $requestId")
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