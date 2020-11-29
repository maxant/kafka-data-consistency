package ch.maxant.kdc.mf.library

import com.fasterxml.jackson.databind.ObjectMapper
import io.smallrye.reactive.messaging.kafka.IncomingKafkaRecordMetadata
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.kafka.common.header.internals.RecordHeader
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.reactive.messaging.Message
import java.math.BigInteger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject

@ApplicationScoped
class ErrorHandler {

    @Inject
    @Channel("errors-out")
    lateinit var errors: Emitter<String>

    @Inject
    @Channel("waitingroom01-out")
    lateinit var waitingroom01: Emitter<String>

    @Inject
    @Channel("waitingroom10-out")
    lateinit var waitingroom10: Emitter<String>

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
        val unwrapped = unwrapThrowableIfRequired(t)
        val value = when(originalMessage) {
            is String -> om.writeValueAsString(Error(unwrapped, originalMessage))
            is Message<*> -> om.writeValueAsString(Error(unwrapped, originalMessage.payload as String))
            else -> throw UnsupportedOperationException("unexpected first parameter type ${originalMessage::class.java.name}")
        }
        val key = when(originalMessage) {
            is String -> null
            is Message<*> -> getKey(originalMessage)
            else -> throw UnsupportedOperationException("unexpected first parameter type ${originalMessage::class.java.name}")
        }

        val ack = CompletableFuture<Unit>()
        val msg = messageWithMetadata(key, value, Headers(
                    context,
                    event = "ERROR"
            ), ack)
        errors.send(msg)
        return ack
    }

    private fun unwrapThrowableIfRequired(t: Throwable) =
        if(t is CompletionException) {
            t.cause ?: t
        } else t

    /**
     * Sends the message to the waiting room so that it can be sent back for a retry.
     * If the record has not been retried, it will be retried again in 1 second, otherwise 10.
     *
     * @param originalMessage the message which lead to this problem
     * @param numRetries how often has this message been retried? one less than the number of times it has been processed.
     */
    fun waitingroom(originalMessage: Any, numRetries: Int): CompletableFuture<Unit> {
        if(originalMessage is Message<*>) {
            val waitShort = numRetries == 0
            val record = originalMessage.getMetadata(IncomingKafkaRecordMetadata::class.java).get()
            val rhs = mutableListOf<RecordHeader>()
            rhs.add(RecordHeader(DELAY_UNTIL, (System.currentTimeMillis() + (if(waitShort) 1_000 else 10_000)).toString().toByteArray()))
            rhs.add(RecordHeader(ORIGINAL_TOPIC, record.topic.toByteArray()))
            record.headers.forEach { rhs.add(RecordHeader(it.key(), it.value())) }
            val ack = CompletableFuture<Unit>()
            val msg = messageWithMetadata(record.key as String, originalMessage.payload as String, rhs, ack)

            if(waitShort) waitingroom01.send(msg)
            else waitingroom10.send(msg)
            return ack
        } else throw UnsupportedOperationException("unexpected first parameter type ${originalMessage::class.java.name}")
    }

    private fun getKey(m: Message<*>) =
            m.getMetadata(IncomingKafkaRecordMetadata::class.java).get().key?.toString()

    companion object {
        const val DELAY_UNTIL = "DELAY_UNTIL"
        const val ORIGINAL_TOPIC = "ORIGINAL_TOPIC"
    }
}

private data class Error(val errorClass: String, val errorMessage: String?, val stackTrace: String, val originalEvent: String, val event: String = "ERROR") {
    constructor(t: Throwable, originalEvent: String) : this(
            t.javaClass.name, t.message, ExceptionUtils.getStackTrace(t), originalEvent
    )
}
