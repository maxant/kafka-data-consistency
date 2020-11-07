package ch.maxant.kdc.mf.library

import com.fasterxml.jackson.databind.ObjectMapper
import io.smallrye.reactive.messaging.kafka.IncomingKafkaRecordMetadata
import org.apache.commons.lang3.exception.ExceptionUtils
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.reactive.messaging.Message
import java.util.concurrent.CompletableFuture
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject

@ApplicationScoped
class ErrorHandler {

    @Inject
    @Channel("errors-out")
    lateinit var errors: Emitter<String>

    @Inject
    lateinit var context: Context

    @Inject
    lateinit var om: ObjectMapper

    /**
     * Provides a transport mechanism for distributing errors back to the microservice which started the
     * processing step in which an error occurs. we need this, since the architecture is async and
     * message based, rather than say sync REST where errors can be more easily propagated back up the
     * call chain to the original caller.
     *
     * can also be considered a dead letter topic. but the idea is that clients subscribe to this topic via
     * the SSE filter in order to receive errors in realtime in order to react to them. a UI for example
     * could then tell the user that a validation error occurred. note that it is very advisable that a client
     * goes and fetches the current application state using REST once such an error is received, because in a
     * microservice environment it could well be inconsistent and need repairing.
     *
     * @param t the exception which resulted
     * @param originalMessage the message which lead to this problem
     */
    fun dlt(originalMessage: Any, t: Throwable): CompletableFuture<Unit> {
        val value = when(originalMessage) {
            is String -> om.writeValueAsString(Error(t, originalMessage))
            is Message<*> -> om.writeValueAsString(Error(t, originalMessage.payload as String))
            else -> throw RuntimeException("unexpected type: ${originalMessage.javaClass.name}")
        }
        val key = when(originalMessage) {
            is String -> null
            is Message<*> -> getKey(originalMessage)
            else -> throw RuntimeException("unexpected type: ${originalMessage.javaClass.name}")
        }

        val ack = CompletableFuture<Unit>()
        val msg = messageWithMetadata(key, value, Headers(
                    context,
                    event = "ERROR"
            ), ack)
        errors.send(msg)
        return ack
    }

    private fun getKey(m: Message<*>) =
            m.getMetadata(IncomingKafkaRecordMetadata::class.java).get().key?.toString()
}

private data class Error(val errorClass: String, val errorMessage: String?, val stackTrace: String, val originalEvent: String, val event: String = "ERROR") {
    constructor(t: Throwable, originalEvent: String) : this(
            t.javaClass.name, t.message, ExceptionUtils.getStackTrace(t), originalEvent
    )
}
