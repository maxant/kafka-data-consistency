package ch.maxant.kdc.mf.web.boundary

import ch.maxant.kdc.mf.library.Context
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.subscription.MultiEmitter
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import org.jboss.logging.Logger
import org.jboss.resteasy.annotations.SseElementType
import java.util.concurrent.CopyOnWriteArrayList
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
    val subscriptions = CopyOnWriteArrayList<EmitterState>()

    fun sendToSubscribers(requestId: String, json: String, key: String) {

        subscriptions
                .filter { it.isExpiredOrCancelled() }
                .forEach {
                    synchronized(it.emitter) { // TODO is this necessary? does it hurt??
                        log.info("closing cancelled/expired emitter for request ${it.requestId}. cancelled: "
                                + "${it.emitter.isCancelled}, expired: ${it.isExpired()}")
                        it.emitter.complete()
                        subscriptions.remove(it)
                    }
                }

        subscriptions
                .filter { it.requestId == requestId || it.matches(key) }
                .filter { !it.isExpiredOrCancelled() }
                .forEach {
                    synchronized(it) { // TODO is this necessary? does it hurt??
                        log.info("emitting request ${it.requestId} to subscriber: $json. context.requestId is ${context.getRequestIdSafely().requestId}")
                        it.touch()
                        it.emitter.emit(json)
                    }
                }
    }

    @GET
    @Path("/stream/{requestId}")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @SseElementType(MediaType.APPLICATION_JSON)
    fun stream(@PathParam("requestId") requestId: String,
               @QueryParam("regex") regexString: String?,
               @javax.ws.rs.core.Context response: HttpServletResponse): Multi<String> {
        // https://serverfault.com/questions/801628/for-server-sent-events-sse-what-nginx-proxy-configuration-is-appropriate
        response.setHeader("Cache-Control", "no-cache")
        response.setHeader("X-Accel-Buffering", "no")
        return Multi.createFrom()
                .emitter { e: MultiEmitter<in String?> ->
                    subscriptions.add(EmitterState(e, requestId, regexString))
                    e.onTermination {
                        e.complete()
                        log.info("removing termindated subscription $requestId")
                        subscriptions.removeIf { it.requestId == requestId }
                    }
                } // TODO if we get memory problems, add a different BackPressureStrategy as a second parameter to the emitter method
    }

    @GET
    @Path("/stats")
    fun stats(): Response = Response.ok("""
        { "subscriptionsCount": ${this.subscriptions.size},
          "subscriptions": ${this.subscriptions.map { it.requestId }} 
        }""".trimIndent().replace(" ", "").replace("\r", "").replace("\n", "")).build()
}

class EmitterState(val emitter: MultiEmitter<in String?>,
                    val requestId: String,
                    regexString: String? = null) {

    private val regex: Regex? = if(regexString == null) null else Regex(regexString)

    private var lastUsed: Long = System.currentTimeMillis()

    fun isExpired() = System.currentTimeMillis() - this.lastUsed > FIVE_MINUTES

    fun isExpiredOrCancelled() = this.emitter.isCancelled || isExpired()

    fun touch() {
        lastUsed = System.currentTimeMillis()
    }

    fun matches(toMatch: String): Boolean {
        return if(regex == null)
                    false
                else
                    toMatch.matches(regex)

    }

    companion object {
        private const val FIVE_MINUTES = 5 * 60 * 1_000
    }
}