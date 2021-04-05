package ch.maxant.kdc.mf.library

import ch.maxant.kdc.mf.library.Context.Companion.COMMAND
import ch.maxant.kdc.mf.library.Context.Companion.REQUEST_ID
import ch.maxant.kdc.mf.library.Context.Companion.SESSION_ID
import io.opentracing.Tracer
import org.jboss.logging.Logger
import org.jboss.logging.MDC
import javax.inject.Inject
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.container.ContainerRequestFilter
import javax.ws.rs.ext.Provider

// TODO probably dont need this as the web filter does the same trick, no?
@Provider
class ContextRequestFilter : ContainerRequestFilter {

    @Inject
    lateinit var context: Context

    @Inject
    lateinit var tracer: Tracer

    val log: Logger = Logger.getLogger(this.javaClass)

    override fun filter(ctx: ContainerRequestContext) {
        context.requestId = getRequestId(ctx)
        context.sessionId = getSessionId(ctx)
        MDC.put(REQUEST_ID, context.requestId)
        MDC.put(SESSION_ID, context.sessionId)
        MDC.put(COMMAND, "${ctx.request.method} ${ctx.uriInfo.requestUri.path}")
        tracer.activeSpan().setTag(REQUEST_ID, context.requestId.requestId)
        tracer.activeSpan().setTag(SESSION_ID, context.sessionId.sessionId)
        tracer.activeSpan().setTag("__origin", "ContextRequestFilter")
    }

    private fun getRequestId(ctx: ContainerRequestContext): RequestId {
        val id = ctx.headers[REQUEST_ID]
        return RequestId(if (id != null && id.isNotEmpty())
            id[0]
        else {
            // it might already be in the request from another filter
            if (context.isRequestIdAlreadySet()) {
                context.requestId.requestId
            } else {
                val rId2 = context.getRequestIdSafely().requestId
                log.debug("creating new requestId as it is missing in the request: $rId2 on path ${ctx.request.method} ${ctx.uriInfo.requestUri.path}")
                rId2
            }
        })
    }

    private fun getSessionId(ctx: ContainerRequestContext): SessionId {
        val id = ctx.headers[SESSION_ID]
        return SessionId(if (id != null && id.isNotEmpty())
            id[0]
        else {
            // it might already be in the request from another filter
            if (context.isSessionIdAlreadySet()) {
                context.sessionId.sessionId
            } else {
                val sId2 = context.getSessionIdSafely().sessionId
                log.debug("creating new sessionId as it is missing in the request: $sId2 on path ${ctx.request.method} ${ctx.uriInfo.requestUri.path}")
                sId2
            }
        })
    }
}
