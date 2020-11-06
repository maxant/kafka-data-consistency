package ch.maxant.kdc.mf.library

import com.fasterxml.jackson.databind.ObjectMapper
import io.smallrye.reactive.messaging.kafka.IncomingKafkaRecordMetadata
import io.smallrye.reactive.messaging.kafka.OutgoingKafkaRecordMetadata
import org.apache.commons.lang3.StringUtils.isNotEmpty
import org.apache.commons.lang3.Validate.isTrue
import org.apache.kafka.common.header.internals.RecordHeader
import org.eclipse.microprofile.reactive.messaging.Message
import org.jboss.logging.Logger
import org.jboss.logging.MDC
import java.util.concurrent.CompletionStage
import javax.enterprise.context.RequestScoped
import javax.inject.Inject
import javax.interceptor.AroundInvoke
import javax.interceptor.Interceptor
import javax.interceptor.InterceptorBinding
import javax.interceptor.InvocationContext

/**
 * deals with exceptions and acks messages so you don't have to. in the case of an exception, the
 * errors are sent to the DLT, unless you set `sendToDlt=false`.<br>
 * <br>
 * ensure you setup log alerting for errors commencing with "EH00*" <br>
 * <br>
 * can be added to incoming handlers which take a string, or a message. <br>
 * <br>
 * adds the requestId, command and event  from the header/root to the MDC so it can be logged
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
        var context: Context,

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

        try {
            context.originalMessage = firstParam
            context.requestId = getRequestId(om, firstParam)
            context.command = getCommand(om, firstParam)
            context.event = getEvent(om, firstParam)
            setMdc(context)
            log.debug("processing incoming message")

            val ret = ctx.proceed()

            return if (ret is CompletionStage<*>) {
                ret.whenComplete { _, t ->
                    if (t != null) {
                        dealWithException(t, ctx)
                    }
                    (firstParam as Message<*>)
                            .ack()
                            .thenRun { log.debug("kafka acked message") }
                            .thenRun { clearMdc() }
                }
            } else {
                // quarkus will ack for us
                ret
            }
        } catch (e: Exception) {
            dealWithException(e, ctx)
            return when (firstParam) {
                is String -> Unit // quarkus will ack for us
                is Message<*> -> firstParam.ack()
                else -> throw java.lang.RuntimeException("EH007 unexpected parameter type should have been caught on method entry")
            }
        }
    }

    fun dealWithException(t: Throwable, ctx: InvocationContext) {
        val firstParam = ctx.parameters[0]
        try {
            when {
                context.requestId.isEmpty -> {
                    log.error("EH003 failed to process message ${asString(firstParam)} " +
                            "- unknown requestId so not sending it to the DLT " +
                            "- this message is being dumped here " +
                            "- this is an error in the program " +
                            "- every message MUST have a requestId header or attribute at the root", t)
                }
                ctx.method.getAnnotation(PimpedAndWithDltAndAck::class.java).sendToDlt -> {
                    log.warn("EH004 failed to process message $firstParam - sending it to the DLT with requestId ${context.requestId}", t)
                    errorHandler.dlt(firstParam, t)
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

    private fun asString(a: Any?): String = when (a) {
        is Message<*> -> a.payload as String
        else -> java.lang.String.valueOf(a)
    }
}

fun getRequestId(om: ObjectMapper, firstParam: Any) = RequestId(getHeader(om, firstParam, REQUEST_ID))

fun getCommand(om: ObjectMapper, firstParam: Any): String = getHeader(om, firstParam, COMMAND)

fun getEvent(om: ObjectMapper, firstParam: Any): String = getHeader(om, firstParam, EVENT)

fun getHeader(om: ObjectMapper, firstParam: Any, header: String): String = when (firstParam) {
    is String -> {
        val msg: String = firstParam
        val root = om.readTree(msg)
        val h = root.get(header)
        if (h == null || h.isNull) {
            ""
        } else {
            h.textValue()
        }
    }
    is Message<*> ->
        String(firstParam
                .getMetadata(IncomingKafkaRecordMetadata::class.java)
                .get()
                .headers
                .find { it.key() == header }
                ?.value()
                ?: byteArrayOf(),
                Charsets.UTF_8)

    else -> throw RuntimeException("unexpected first parameter type")
}

const val REQUEST_ID = "requestId"
const val COMMAND = "command"
const val EVENT = "event"

data class RequestId(val requestId: String) {
    override fun toString(): String = requestId
    val isEmpty = requestId.isEmpty()
}

data class Headers(val requestId: RequestId,
                   val command: String? = null,
                   val event: String? = null,
                   val originalCommand: String?,
                   val originalEvent: String?) {
    constructor(context: Context,
                command: String? = null,
                event: String? = null
    ) : this(context.requestId,
            command,
            event,
            context.command,
            context.event)
}

fun messageWithMetadata(key: String?, value: String, headers: Headers): Message<String> {
    val headersList = mutableListOf(RecordHeader(REQUEST_ID, headers.requestId.toString().toByteArray()))
    if(isNotEmpty(headers.command)) headersList.add(RecordHeader(COMMAND, headers.command!!.toByteArray()))
    if(isNotEmpty(headers.event)) headersList.add(RecordHeader(EVENT, headers.event!!.toByteArray()))
    if(isNotEmpty(headers.originalCommand)) headersList.add(RecordHeader("originalCommand", headers.originalCommand!!.toByteArray()))
    if(isNotEmpty(headers.originalEvent)) headersList.add(RecordHeader("originalEvent", headers.originalEvent!!.toByteArray()))
    val metadata = OutgoingKafkaRecordMetadata.builder<Any>()
            .withKey(key)
            .withHeaders(headersList)
            .build()
    return Message.of(value).addMetadata(metadata)

}

fun setMdc(context: Context) {
    MDC.put(REQUEST_ID, context.requestId)
    MDC.put(COMMAND, context.command)
    MDC.put(EVENT, context.event)
}

fun clearMdc() {
    MDC.remove(REQUEST_ID)
    MDC.remove(EVENT)
    MDC.remove(COMMAND)
}

