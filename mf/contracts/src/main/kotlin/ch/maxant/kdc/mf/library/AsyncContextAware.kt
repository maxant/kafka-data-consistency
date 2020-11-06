package ch.maxant.kdc.mf.library

import org.eclipse.microprofile.context.ManagedExecutor
import org.eclipse.microprofile.context.ThreadContext
import java.util.concurrent.CompletableFuture
import javax.enterprise.context.RequestScoped
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
 * Propagates the requestId too.
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
        var threadContext: ThreadContext,

        @Inject
        var managedExecutor: ManagedExecutor,

        @Inject
        var context: Context
) {
    @AroundInvoke
    fun invoke(ctx: InvocationContext): Any? {

        // copy elements out, as the proxy might not work inside the supplier?
        val requestId = context.requestId
        val command = context.command
        val event = context.event
        val originalMessage = context.originalMessage

        return managedExecutor.supplyAsync(threadContext.contextualSupplier {

            setMdc(Context.of(requestId, originalMessage, command, event))

            val r = ctx.proceed()

            clearMdc()

            // get, coz otherwise we end up with a Future<Future<T>> rather than just Future<T>
            // im assuming Java EE impls do this too where they impl @Async?
            (r as CompletableFuture<*>).get()
        })
    }
}