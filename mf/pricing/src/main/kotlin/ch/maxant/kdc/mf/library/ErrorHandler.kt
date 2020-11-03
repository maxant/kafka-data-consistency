package ch.maxant.kdc.mf.library

import com.fasterxml.jackson.databind.ObjectMapper
import io.smallrye.reactive.messaging.kafka.IncomingKafkaRecordMetadata
import org.apache.commons.lang3.Validate.isTrue
import org.apache.commons.lang3.exception.ExceptionUtils
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.reactive.messaging.Message
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
     * @param t the exception which resulted
     * @param originalEvent the event which lead to this problem
     */
    fun dlt(requestId: String?, t: Throwable, originalEvent: String) {
        errors.send(om.writeValueAsString(Error(requestId, t, originalEvent)))
    }
}

private data class Error(val requestId: String?, val errorClass: String, val errorMessage: String?, val stackTrace: String, val originalEvent: String, val event: String = "ERROR") {
    constructor(requestId: String?, t: Throwable, originalEvent: String) : this(
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
        val firstParam = ctx.parameters[0]
        isTrue((firstParam is String) || (firstParam is Message<*>),
                "EH002 @ErrorHandled on method ${ctx.target.javaClass.name}::${ctx.method.name} must contain one String/Message parameter")
        return try {
            ctx.proceed()
        } catch (e: Exception) {
            dealWithException(e, ctx)
            return when (firstParam) {
                is String -> Unit
                is Message<*> -> firstParam.ack()
                else -> throw java.lang.RuntimeException("EH007 unexpected parameter type should have been caught a few lines above")
            }
        }
    }

    fun dealWithException(e: Exception, ctx: InvocationContext) {
        val firstParam = ctx.parameters[0]
        try {
            val requestId: String? = getRequestId(om, firstParam)
            when {
                requestId == null -> {
                    log.error("EH003 failed to process message ${asString(firstParam)} " +
                            "- unknown requestId so not sending it to the DLT " +
                            "- this message is being dumped here " +
                            "- this is an error in the program " +
                            "- every message MUST have a requestId attribute at the root", e)
                }
                ctx.method.getAnnotation(ErrorsHandled::class.java).sendToDlt -> {
                    log.warn("EH004 failed to process message $firstParam - sending it to the DLT with requestId $requestId", e)
                    errorHandler.dlt(requestId, e, asString(firstParam))
                }
                else -> {
                    log.error("EH005 failed to process message ${asString(firstParam)} " +
                            "- NOT sending it to the DLT because @ErrorsHandled is configured with 'sendToDlt = false' " +
                            "- this message is being dumped here", e)
                }
            }
        } catch (e2: Exception) {
            log.error("EH006a failed to process message $firstParam - this message is being dumped here", e2)
            log.error("EH006b original exception was", e)
        }
    }

    private fun asString(a: Any?): String = when (a) {
        is Message<*> -> a.payload as String
        else -> java.lang.String.valueOf(a)
    }

    private fun getRequestId(om: ObjectMapper, firstParam: Any): String? = when (firstParam) {
        is String -> {
            val msg: String = firstParam
            val root = om.readTree(msg)
            val rId = root.get("requestId")
            if (rId.isNull) {
                null
            } else {
                rId.textValue()
            }
        }
        is Message<*> -> {
            String(firstParam
                    .getMetadata(IncomingKafkaRecordMetadata::class.java)
                    .get()
                    .headers
                    .find { it.key() == "requestId" }!!
                    .value(),
                    Charsets.UTF_8)
        }
        else -> throw RuntimeException("unexpected parameter type")
    }
}