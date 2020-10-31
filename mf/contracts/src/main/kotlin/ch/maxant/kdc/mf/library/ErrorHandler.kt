package ch.maxant.kdc.mf.library

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.lang3.exception.ExceptionUtils
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
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
     * Provides a transport mechanism for distributing errors back to the system which started the
     * processing context in which an error occurs. we need this, since the architecture is async and
     * message based, rather than say sync REST where errors can be more easily propagated back up the
     * call chain to the original caller.
     *
     * can also be considered a dead letter topic. but the idea is that clients subscribe to this topic via
     * the infra component in order to receive errors in realtime in order to react to them. a UI for example
     * could then tell the user that a validation error occurred. note that it is very advisable that a client
     * goes and fetches the current application state using REST once such an error is received, because in a
     * microservice environment it could well be inconsistent and need repairing.
     *
     * @param referenceId e.g. a contractId or something functional, describing the context in which this problem occurred
     * @param originalEvent the event which lead to this problem
     * @param t the exception which resulted
     */
    fun dlt(referenceId: String?, t: Throwable, originalEvent: String) {
        errors.send(om.writeValueAsString(Error(referenceId, t, originalEvent)))
    }
}

private data class Error(val referenceId: String?, val errorClass: String, val errorMessage: String?, val stackTrace: String, val originalEvent: String, val event: String = "ERROR") {
    constructor(referenceId: String?, t: Throwable, originalEvent: String): this(
            referenceId, t.javaClass.name, t.message, ExceptionUtils.getStackTrace(t), originalEvent
    )
}