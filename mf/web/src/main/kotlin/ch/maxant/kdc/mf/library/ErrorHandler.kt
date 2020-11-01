package ch.maxant.kdc.mf.library

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.lang3.Validate.isTrue
import org.apache.commons.lang3.exception.ExceptionUtils
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.jboss.logging.Logger
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.interceptor.AroundInvoke
import javax.interceptor.Interceptor
import javax.interceptor.InterceptorBinding
import javax.interceptor.InvocationContext

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
     * @param originalEvent the event which lead to this problem
     * @param t the exception which resulted
     */
    fun dlt(requestId: String?, t: Throwable, originalEvent: String) {
        errors.send(om.writeValueAsString(Error(requestId, t, originalEvent)))
    }
}

private data class Error(val requestId: String?, val errorClass: String, val errorMessage: String?, val stackTrace: String, val originalEvent: String, val event: String = "ERROR") {
    constructor(requestId: String?, t: Throwable, originalEvent: String): this(
            requestId, t.javaClass.name, t.message, ExceptionUtils.getStackTrace(t), originalEvent
    )
}

@InterceptorBinding
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ErrorsHandled(val sendToDlt: Boolean = true)

@ErrorsHandled
@Interceptor
@SuppressWarnings("unused")
class ErrorsInterceptor(
        @Inject
        var errorHandler: ErrorHandler,

        @Inject
        var log: Logger,

        @Inject
        var om: ObjectMapper
) {
    @AroundInvoke
    fun handleErrors(ctx: InvocationContext): Any? {
        isTrue(ctx.parameters.size == 1,
                "EH001 @ErrorHandled on method ${ctx.target.javaClass.name}::${ctx.method.name} must contain exactly one parameter")
        isTrue(ctx.parameters[0].javaClass.name == String::class.java.name,
                "EH002 @ErrorHandled on method ${ctx.target.javaClass.name}::${ctx.method.name} must contain one string parameter")
        return try {
            ctx.proceed()
        } catch (e: Exception) {
            dealWithException(e, ctx)
        }
    }

    fun dealWithException(e: Exception, ctx: InvocationContext) {
        try {
            val msg: String = ctx.parameters[0] as String
            val root = om.readTree(msg)
            val requestId = root.get("requestId")
            if (requestId == null || requestId.isNull) {
                log.error("EH003 failed to process message $msg " +
                        "- unknown requestId so not sending it to the DLT " +
                        "- this message is being dumped here " +
                        "- this is an error in the program " +
                        "- every message MUST have a requestId attribute at the root", e)
            } else if(ctx.method.getAnnotation(ErrorsHandled::class.java).sendToDlt) {
                log.warn("EH004 failed to process message $msg - sending it to the DLT with requestId $requestId", e)
                errorHandler.dlt(requestId.textValue(), e, msg)
            } else {
                log.error("EH005 failed to process message $msg " +
                        "- NOT sending it to the DLT because @ErrorsHandled is configured with 'sendToDlt = false' " +
                        "- this message is being dumped here", e)
            }
        } catch (e2: Exception) {
            log.error("EH006a failed to process message ${ctx.parameters[0]} - this message is being dumped here", e2)
            log.error("EH006b original exception was", e)
        }
    }
}