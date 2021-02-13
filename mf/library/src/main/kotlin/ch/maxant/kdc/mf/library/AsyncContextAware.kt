package ch.maxant.kdc.mf.library

import ch.maxant.kdc.mf.library.Context.Companion.REQUEST_ID
import io.opentracing.Tracer
import org.eclipse.microprofile.context.ManagedExecutor
import org.eclipse.microprofile.context.ThreadContext
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.interceptor.AroundInvoke
import javax.interceptor.Interceptor
import javax.interceptor.InterceptorBinding
import javax.interceptor.InvocationContext

/**
 * Uses the managed executor and thread context to execute the method async, with the current context
 * @return MUST return a CompletionStage. Tip: use `CompletableFuture.completedFuture(...)` <br>
 * <br>
 * Quarkus requires us to do entity manager stuff on a worker thread if we work with Message rather than
 * String, as it requires us to return a CompletionStage rather than Unit. its not compatible with
 * @Blocking either<br>
 * <br>
 * Propagates the requestId and MDC (logging) too.
 */
@InterceptorBinding
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AsyncContextAware

@AsyncContextAware
@Interceptor
@SuppressWarnings("unused")
class AsyncContextAwareInterceptor(
        @Inject
        val threadContext: ThreadContext,

        @Inject
        val managedExecutor: ManagedExecutor,

        @Inject
        val context: Context,

        @Inject
        val tracer: Tracer
) {
    @AroundInvoke
    fun invoke(ctx: InvocationContext): Any {

        // copy elements out, as the proxy might not work inside the supplier, and MDC isnt propagated coz smallrye doesnt know about it
        val copyOfContext = Context.of(context)

        // seems that the new span isn't correctly propagated, so lets insert a new one, to ensure it is
        val parent = tracer.activeSpan()

        return managedExecutor.supplyAsync(threadContext.contextualSupplier {
            withMdcSet(copyOfContext) {
                val newScope = tracer.buildSpan("${ctx.method.declaringClass.name}.${ctx.method.name} (async)")
                    .asChildOf(parent)
                    .startActive(true)
                newScope.span().setTag(REQUEST_ID, copyOfContext.requestId.toString())
                newScope.span().setTag("__origin", "AsyncContextAware")
                try {
                    ctx.proceed() as CompletionStage<Any>
                } finally {
                    newScope.close()
                }
            }
        }).thenCompose { it } // unwrap the nested CS from type CS<CS<Any>> to CS<Any>

    }
}