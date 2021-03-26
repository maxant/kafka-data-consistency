package ch.maxant.kdc.objects;

import org.eclipse.microprofile.context.ThreadContext;

import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Ensures that the context is transported to subsequent callbacks that may be executed on other threads.
 */
@ContextAware
@Interceptor
public class ContextAwareInterceptor {

    @Inject
    ThreadContext threadContext;

    @AroundInvoke
    public Object exec(InvocationContext ctx) throws Exception {
        Object o = ctx.proceed();
        if(o instanceof CompletableFuture) {
            return threadContext.withContextCapture((CompletableFuture) o);
        } else if(o instanceof CompletionStage) {
            return threadContext.withContextCapture((CompletionStage)o);
        } else throw new UnsupportedOperationException("unsupported type " + o.getClass());
    }
}
