package ch.maxant.kdc.mf.library

import com.fasterxml.jackson.databind.ObjectMapper
import io.smallrye.reactive.messaging.kafka.OutgoingKafkaRecordMetadata
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.kafka.common.header.internals.RecordHeader
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.reactive.messaging.Message
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject

@ApplicationScoped
class ErrorHandler {

    @Inject
    @Channel("errors-out")
    lateinit var errors: Emitter<String>

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
     * @param requestId originates from the service which started the process step, so that they can get feedback about this error
     * @param key from the original message, or null if unknown
     * @param t the exception which resulted
     * @param originalEvent the event which lead to this problem
     */
    // TODO take the original message, rather than the string - that way we have the request Id too
    fun dlt(requestId: String?, key: String?, t: Throwable, originalEvent: String) {
        val metadata = OutgoingKafkaRecordMetadata.builder<Any>()
                .withKey(key)
                .withHeaders(listOf(RecordHeader(REQUEST_ID, requestId.toString().toByteArray())))
                .build()
        val payload = om.writeValueAsString(Error(requestId, t, originalEvent))
        errors.send(Message.of(payload).addMetadata(metadata))
    }
}

private data class Error(val requestId: String?, val errorClass: String, val errorMessage: String?, val stackTrace: String, val originalEvent: String, val event: String = "ERROR") {
    constructor(requestId: String?, t: Throwable, originalEvent: String) : this(
            requestId, t.javaClass.name, t.message, ExceptionUtils.getStackTrace(t), originalEvent
    )
}
