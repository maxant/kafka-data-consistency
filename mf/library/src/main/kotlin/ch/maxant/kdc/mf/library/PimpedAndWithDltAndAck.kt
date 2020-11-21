package ch.maxant.kdc.mf.library

import ch.maxant.kdc.mf.library.Context.Companion.COMMAND
import ch.maxant.kdc.mf.library.Context.Companion.DEMO_CONTEXT
import ch.maxant.kdc.mf.library.Context.Companion.EVENT
import ch.maxant.kdc.mf.library.Context.Companion.REQUEST_ID
import ch.maxant.kdc.mf.library.Context.Companion.RETRY_COUNT
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.smallrye.reactive.messaging.kafka.IncomingKafkaRecordMetadata
import io.smallrye.reactive.messaging.kafka.OutgoingKafkaRecordMetadata
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.StringUtils.isNotEmpty
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.kafka.common.header.internals.RecordHeader
import org.eclipse.microprofile.reactive.messaging.Message
import org.jboss.logging.Logger
import org.jboss.logging.MDC
import java.lang.UnsupportedOperationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.interceptor.AroundInvoke
import javax.interceptor.Interceptor
import javax.interceptor.InterceptorBinding
import javax.interceptor.InvocationContext
import javax.validation.ConstraintViolationException
import javax.validation.ValidationException

/**
 * deals with exceptions and acks messages so you don't have to. in the case of an exception, the
 * errors are sent to the DLT, unless you set `sendToDlt=false`.<br>
 * <br>
 * ensure you setup log alerting for errors commencing with "EH00*" <br>
 * <br>
 * can be added to incoming handlers which take a string, or a message. <br>
 * <br>
 * adds the requestId, command and event from the header/root to the MDC so it can be logged
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
        require(ctx.parameters.size == 1) { "EH001 @PimpedAndWithDltAndAck on method ${ctx.target.javaClass.name}::${ctx.method.name} must contain exactly one parameter" }
        val firstParam = ctx.parameters[0]
        require((firstParam is String) || (firstParam is Message<*>)) { "EH002 @PimpedAndWithDltAndAck on method ${ctx.target.javaClass.name}::${ctx.method.name} must contain one String/Message parameter" }

        // set context into request scoped bean, so downstream can use it. also works with quarkus context propogation
        setContext(firstParam)

        // take a copy, in order to fill MDC before logging in callbacks
        val copyOfContext = Context.of(context)

        // set/clear MDC for this thread and proceed into intercepted code
        return withMdcSet(context) {
            proceed(copyOfContext, ctx, firstParam)
        }
    }

    private data class ResultAndError(val result: Any?, val throwable: Throwable?)

    fun proceed(copyOfContext: Context, ctx: InvocationContext, firstParam: Any?): Any? {
        try {
            log.debug("processing incoming message")

            val ret = ctx.proceed()

            return if (ret is CompletionStage<*>) {
                ret.handle { s, t -> ResultAndError(s, t) }
                   .thenCompose { rae -> dealWithExceptionIfNecessary(rae.throwable, ctx, copyOfContext) }
                   .thenCompose { (firstParam as Message<*>).ack() }
                   .thenRun { withMdcSet(copyOfContext) { log.debug("kafka acked message") } }
            } else {
                // quarkus will ack for us
                ret
            }
        } catch (e: Exception) {
            val cf1 = dealWithExceptionIfNecessary(e, ctx, copyOfContext)
            return when (firstParam) {
                is String -> cf1.get() // we have no choice but to block, or skip, (since we can't return a CS) but if downstream fails, we should too; quarkus will ack the incoming message for us as the interface is string base
                is Message<*> -> cf1.thenCompose { firstParam.ack() }.thenRun { log.debug("kafka acked message") }
                else -> throw java.lang.RuntimeException("EH007 unexpected parameter type should have been caught on method entry")
            }
        }
    }

    private fun setContext(firstParam: Any) {
        context.originalMessage = firstParam
        context.requestId = getRequestId(om, firstParam)
        context.retryCount = getRetryCount(om, firstParam)
        context.demoContext = getDemoContext(om, firstParam)
        context.command = getCommand(om, firstParam)
        context.event = getEvent(om, firstParam)
    }

    fun dealWithExceptionIfNecessary(t: Throwable?, ctx: InvocationContext, copyOfContext: Context): CompletableFuture<Unit> {
        var ret: CompletableFuture<Unit> = completedFuture(null)
        if(t == null) {
            return ret
        }
        withMdcSet(copyOfContext) {
            val firstParam = ctx.parameters[0]
            try {
                when {
                    copyOfContext.requestId.isEmpty -> {
                        log.error("EH003 failed to process message ${asString(firstParam)} " +
                                "- unknown requestId so not sending it to the DLT " +
                                "- this message is being dumped here " +
                                "- this is an error in the program " +
                                "- every message MUST have a requestId header or attribute at the root", t)
                    }
                    ctx.method.getAnnotation(PimpedAndWithDltAndAck::class.java).sendToDlt -> {
                        ret = sendToWaitingroomOrDlt(firstParam, copyOfContext, t)
                    }
                    else -> {
                        log.error("EH005 failed to process message ${asString(firstParam)} " +
                                "- NOT sending it to the DLT because @PimpedWithDlt is configured with 'sendToDlt = false' " +
                                "- this message is being dumped here", t)
                    }
                }
            } catch (e2: Exception) {
                log.error("EH006a failed to process message ${asString(firstParam)} - this message is being dumped here", e2)
                log.error("EH006b original exception was", t)
            }
        }
        return ret
    }

    private fun sendToWaitingroomOrDlt(firstParam: Any, copyOfContext: Context, t: Throwable): CompletableFuture<Unit> =
        if(isRetryableException(firstParam, t)) {
            if(copyOfContext.retryCount < 3) {
                log.info("EH004a failed to process message due to a retryable exception with a retry count of ${copyOfContext.retryCount}. " +
                        "message was ${asString(firstParam)}" +
                        " - sending it to the waiting room as it can be retried, with requestId ${copyOfContext.requestId}", t)
                errorHandler.waitingroom(firstParam, copyOfContext.retryCount)
            } else {
                log.warn("EH004b failed to process message due to a retryable exception but with a retry count of ${copyOfContext.retryCount}. " +
                        "message was ${asString(firstParam)}" +
                        " - sending it to the DLT with requestId ${copyOfContext.requestId}", t)
                errorHandler.dlt(firstParam, t)
            }
        } else {
            log.warn("EH004c failed to process message due to a non-retryable exception. " +
                    "message was ${asString(firstParam)}" +
                    " - sending it to the DLT with requestId ${copyOfContext.requestId}", t)
            errorHandler.dlt(firstParam, t)
        }

    private fun isRetryableException(firstParam: Any, t: Throwable) =
            when (firstParam) {
                is String -> false // we have no way of checking how often the message has been retried, so cannot
                                   // give up => we simply dont support retry when you use basic strings instead of message objects
                is Message<*> -> {
                    val throwables = ExceptionUtils.getThrowableList(t).map { it::class.java }
                    throwables.none {
                               it == ValidationException::class.java
                            || it == ConstraintViolationException::class.java
                            || it == IllegalArgumentException::class.java
                    }
                }
                else -> throw UnsupportedOperationException("unexpected first parameter type ${firstParam::class.java.name}")
            }

    private fun asString(a: Any?): String = when (a) {
        is Message<*> -> a.payload as String
        else -> java.lang.String.valueOf(a)
    }
}

fun getRequestId(om: ObjectMapper, firstParam: Any) = RequestId(getHeader(om, firstParam, REQUEST_ID))

fun getRetryCount(om: ObjectMapper, firstParam: Any) = getHeader(om, firstParam, RETRY_COUNT).toIntOrNull()?:0

fun getDemoContext(om: ObjectMapper, firstParam: Any): DemoContext {
    var raw = getHeader(om, firstParam, DEMO_CONTEXT)
    raw = if(StringUtils.isEmpty(raw)) "{}" else raw
    val dc = om.readValue<DemoContext>(raw)
    dc.json = raw
    return dc
}

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
                .orElse(null)
                ?.headers
                ?.find { it.key() == header }
                ?.value()
                ?: byteArrayOf(),
                Charsets.UTF_8)

    else -> throw UnsupportedOperationException("unexpected first parameter type ${firstParam::class.java.name}")
}

data class RequestId(val requestId: String) {
    override fun toString(): String = requestId
    val isEmpty = requestId.isEmpty()
}

data class Headers(val requestId: RequestId,
                   val demoContext: DemoContext? = null,
                   val command: String? = null,
                   val event: String? = null,
                   val originalCommand: String?,
                   val originalEvent: String?) {
    constructor(context: Context,
                command: String? = null,
                event: String? = null
    ) : this(context.requestId,
            context.demoContext,
            command,
            event,
            context.command,
            context.event)
}

/**
 * @param ack a future which will be completed when the message is acked
 */
fun messageWithMetadata(key: String?, value: String, headers: Headers,
                        ack: CompletableFuture<Unit>): Message<String> {
//println("sending value: $value")
    val headersList = mutableListOf(RecordHeader(REQUEST_ID, headers.requestId.toString().toByteArray()))
    if(headers.demoContext != null) headersList.add(RecordHeader(DEMO_CONTEXT, (headers.demoContext.json?:"").toByteArray()))
    if(isNotEmpty(headers.command)) headersList.add(RecordHeader(COMMAND, headers.command!!.toByteArray()))
    if(isNotEmpty(headers.event)) headersList.add(RecordHeader(EVENT, headers.event!!.toByteArray()))
    if(isNotEmpty(headers.originalCommand)) headersList.add(RecordHeader("originalCommand", headers.originalCommand!!.toByteArray()))
    if(isNotEmpty(headers.originalEvent)) headersList.add(RecordHeader("originalEvent", headers.originalEvent!!.toByteArray()))
    return messageWithMetadata(key, value, headersList, ack)
}

/**
 * @param ack a future which will be completed when the message is acked
 */
fun messageWithMetadata(key: String?, value: String, headers: List<RecordHeader>,
                        ack: CompletableFuture<Unit>): Message<String> {
//println("sending value: $value")
    val metadata = OutgoingKafkaRecordMetadata.builder<Any>()
            .withKey(key)
            .withHeaders(headers)
            .build()
    val ackSupplier: () -> CompletableFuture<Void> = { ack.complete(null); completedFuture(null) }
    return Message.of(value, ackSupplier).addMetadata(metadata)

}

private fun setMdc(context: Context) {
    MDC.put(REQUEST_ID, context.requestId)
    MDC.put(COMMAND, context.command)
    MDC.put(EVENT, context.event)
}

private fun clearMdc() {
    MDC.remove(REQUEST_ID)
    MDC.remove(EVENT)
    MDC.remove(COMMAND)
}

fun <U> withMdcSet(context: Context, f: () -> U) =
        try {
            setMdc(context)
            f()
        } finally {
            clearMdc()
        }

