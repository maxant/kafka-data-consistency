package ch.maxant.kdc.mf.library

import org.jboss.logging.Logger
import org.jboss.logging.MDC
import java.util.*
import javax.inject.Inject
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.container.ContainerRequestFilter
import javax.ws.rs.ext.Provider

@Provider
class ContextRequestFilter : ContainerRequestFilter {

    @Inject
    lateinit var context: Context

    val log: Logger = Logger.getLogger(this.javaClass)

    //?@kotlin.Throws(IOException::class)
    override fun filter(ctx: ContainerRequestContext) {
        val requestId = ctx.headers[REQUEST_ID]
        val rId = if(requestId != null && requestId.isNotEmpty())
            requestId[0]
        else {
            log.info("creating requestId as it is missing in the request")
            UUID.randomUUID().toString()
        }
        context.requestId = RequestId(rId)
        MDC.put(REQUEST_ID, context.requestId)
        MDC.put(COMMAND, "${ctx.request.method} ${ctx.uriInfo.requestUri.path}")
    }
}