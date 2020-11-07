package ch.maxant.kdc.mf.library

import org.jboss.logging.MDC
import javax.inject.Inject
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.container.ContainerRequestFilter
import javax.ws.rs.ext.Provider

@Provider
class ContextFilter : ContainerRequestFilter {

    @Inject
    lateinit var context: Context

    //?@kotlin.Throws(IOException::class)
    override fun filter(ctx: ContainerRequestContext) {
        val requestId = ctx.headers[REQUEST_ID]
        if(requestId != null && requestId.isNotEmpty()) {
            context.requestId = RequestId(requestId[0])
            MDC.put(REQUEST_ID, context.requestId)
            MDC.put(COMMAND, "${ctx.request.method} ${ctx.uriInfo.requestUri.path}")
        }
    }
}