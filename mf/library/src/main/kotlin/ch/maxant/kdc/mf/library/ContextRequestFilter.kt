package ch.maxant.kdc.mf.library

import ch.maxant.kdc.mf.library.Context.Companion.COMMAND
import ch.maxant.kdc.mf.library.Context.Companion.REQUEST_ID
import org.jboss.logging.Logger
import org.jboss.logging.MDC
import java.util.*
import javax.inject.Inject
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.container.ContainerRequestFilter
import javax.ws.rs.ext.Provider

// TODO probably dont need this as the web filter does the same trick, no?
@Provider
class ContextRequestFilter : ContainerRequestFilter {

    @Inject
    lateinit var context: Context

    val log: Logger = Logger.getLogger(this.javaClass)

    override fun filter(ctx: ContainerRequestContext) {
        context.requestId = getRequestId(ctx)
        MDC.put(REQUEST_ID, context.requestId)
        MDC.put(COMMAND, "${ctx.request.method} ${ctx.uriInfo.requestUri.path}")
    }

    private fun getRequestId(ctx: ContainerRequestContext): RequestId {
        val requestId = ctx.headers[REQUEST_ID]
        val rId = if (requestId != null && requestId.isNotEmpty())
            requestId[0]
        else {
            log.info("creating requestId as it is missing in the request")
            UUID.randomUUID().toString()
        }
        return RequestId(rId)
    }
}