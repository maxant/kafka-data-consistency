package ch.maxant.kdc.mf.billing.boundary

import io.quarkus.runtime.StartupEvent
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.subscription.MultiEmitter
import org.jboss.resteasy.annotations.SseElementType
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.enterprise.event.Observes
import javax.servlet.http.HttpServletResponse
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.Context
import javax.ws.rs.core.MediaType

@Path("/test")
@SuppressWarnings("unused")
class SseTest {

    val subscriptions = ConcurrentHashMap<String, EmitterState>()

    @SuppressWarnings("unused")
    fun init(@Observes e: StartupEvent) {
        Multi.createFrom()
            .ticks()
            .every(Duration.ofSeconds(1))
            .subscribe().with { sendToSubscribers("$it") }
    }

    fun sendToSubscribers(data: String) {
        val toRemove = mutableSetOf<String>()
        subscriptions
            .entries
            .filter { it.value.isExpiredOrCancelled() }
            .forEach {
                synchronized(it.value.emitter) { // TODO is this necessary? does it hurt??
                    it.value.emitter.complete()
                    toRemove.add(it.key)
                }
            }
        toRemove.forEach { subscriptions.remove(it) }

        subscriptions
            .entries
            .filter { !it.value.isExpiredOrCancelled() }
            .forEach {
                synchronized(it.value) { // TODO is this necessary? does it hurt??
                    it.value.emitter.emit(data)
                }
            }
    }

    @GET
    @Path("/sse")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @SseElementType(MediaType.APPLICATION_JSON)
    fun notifications(@Context response: HttpServletResponse): Multi<String> {
        // https://serverfault.com/questions/801628/for-server-sent-events-sse-what-nginx-proxy-configuration-is-appropriate
        response.setHeader("Cache-Control", "no-cache")
        response.setHeader("X-Accel-Buffering", "no")
        return Multi.createFrom()
            .emitter { e: MultiEmitter<in String?> ->
                val id = UUID.randomUUID().toString()
                subscriptions[id] = EmitterState(e, System.currentTimeMillis())
                e.onTermination {
                    e.complete()
                    subscriptions.remove(id)
                }
            } // TODO if we get memory problems, add a different BackPressureStrategy as a second parameter to the emitter method
    }
}
