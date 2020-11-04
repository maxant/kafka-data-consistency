package ch.maxant.kdc.mf.library

import com.fasterxml.jackson.databind.ObjectMapper
import io.smallrye.reactive.messaging.kafka.IncomingKafkaRecordMetadata
import org.apache.commons.lang3.Validate.isTrue
import org.eclipse.microprofile.reactive.messaging.Message
import org.jboss.logging.Logger
import org.jboss.logging.MDC
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.interceptor.AroundInvoke
import javax.interceptor.Interceptor
import javax.interceptor.InterceptorBinding
import javax.interceptor.InvocationContext

/**
 * deals with exceptions and acks messages so you don't have to. in the case of an exception, the
 * errors are sent to the DLT, unless you set `sendToDlt=false`. ensure you setup log alerting for
 * errors commencing with "EH00*" <br>
 * <br>
 * can be added to incoming handlers which take a string, or a message. <br>
 * <br>
 * adds the requestId from the header to the MDC so it can be logged
 */
@InterceptorBinding
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class PimpedAndWithDltAndAck(val sendToDlt: Boolean = true)

@PimpedAndWithDltAndAck
@Interceptor
@SuppressWarnings("unused")
class PimpedAndWithDltAndAckInterceptor(
        @Inject
        var errorHandler: ErrorHandler,

        @Inject
        var om: ObjectMapper
) {
    var log: Logger = Logger.getLogger(this.javaClass)

    @AroundInvoke
    fun invoke(ctx: InvocationContext): Any? {
        isTrue(ctx.parameters.size == 1,
                "EH001 @ErrorHandled on method ${ctx.target.javaClass.name}::${ctx.method.name} must contain exactly one parameter")
        val firstParam = ctx.parameters[0]
        isTrue((firstParam is String) || (firstParam is Message<*>),
                "EH002 @ErrorHandled on method ${ctx.target.javaClass.name}::${ctx.method.name} must contain one String/Message parameter")

        val requestId = getRequestId(om, firstParam)
        MDC.put(REQUEST_ID, requestId)

        try {
            log.debug("processing incoming message")
            val ret = ctx.proceed()
            return if (ret is CompletionStage<*>) {
                ret.whenComplete { _, t ->
                    if(t != null) {
                        dealWithException(t, ctx)
                    }
                    (firstParam as Message<*>).ack()
                            .thenRun { log.debug("kafka acked message") }
                            .thenRun { MDC.remove(REQUEST_ID)}
                }
            } else {
                ret
            }
        } catch (e: Exception) {
            dealWithException(e, ctx)
            return when (firstParam) {
                is String -> Unit
                is Message<*> -> firstParam.ack()
                else -> throw java.lang.RuntimeException("EH007 unexpected parameter type should have been caught on method entry")
            }
        }
    }

    fun dealWithException(t: Throwable, ctx: InvocationContext) {
        val firstParam = ctx.parameters[0]
        try {
            val requestId: String? = getRequestId(om, firstParam)
            when {
                requestId == null -> {
                    log.error("EH003 failed to process message ${asString(firstParam)} " +
                            "- unknown requestId so not sending it to the DLT " +
                            "- this message is being dumped here " +
                            "- this is an error in the program " +
                            "- every message MUST have a requestId attribute at the root", t)
                }
                ctx.method.getAnnotation(PimpedAndWithDltAndAck::class.java).sendToDlt -> {
                    log.warn("EH004 failed to process message $firstParam - sending it to the DLT with requestId $requestId", t)
                    errorHandler.dlt(requestId, getKey(firstParam), t, asString(firstParam))
                }
                else -> {
                    log.error("EH005 failed to process message ${asString(firstParam)} " +
                            "- NOT sending it to the DLT because @PimpedWithDlt is configured with 'sendToDlt = false' " +
                            "- this message is being dumped here", t)
                }
            }
        } catch (e2: Exception) {
            log.error("EH006a failed to process message $firstParam - this message is being dumped here", e2)
            log.error("EH006b original exception was", t)
        }
    }

    private fun getKey(a: Any?): String? = when (a) {
        is Message<*> -> a.getMetadata(IncomingKafkaRecordMetadata::class.java).get().key?.toString()
        else -> null
    }

    private fun asString(a: Any?): String = when (a) {
        is Message<*> -> a.payload as String
        else -> java.lang.String.valueOf(a)
    }

    private fun getRequestId(om: ObjectMapper, firstParam: Any): String? = when (firstParam) {
        is String -> {
            val msg: String = firstParam
            val root = om.readTree(msg)
            val rId = root.get(REQUEST_ID)
            if (rId.isNull) {
                null
            } else {
                rId.textValue()
            }
        }
        is Message<*> -> getRequestId(firstParam)
        else -> throw RuntimeException("unexpected parameter type")
    }
}

fun getRequestId(msg: Message<*>) =
    String(msg
            .getMetadata(IncomingKafkaRecordMetadata::class.java)
            .get()
            .headers
            .find { it.key() == REQUEST_ID }!!
            .value(),
            Charsets.UTF_8)

const val REQUEST_ID = "requestId"
