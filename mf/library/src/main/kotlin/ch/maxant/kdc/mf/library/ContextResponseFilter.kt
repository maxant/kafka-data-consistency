package ch.maxant.kdc.mf.library

import ch.maxant.kdc.mf.library.Context.Companion.REQUEST_ID
import org.jboss.logging.Logger
import org.jboss.logging.MDC
import javax.inject.Inject
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.container.ContainerResponseContext
import javax.ws.rs.container.ContainerResponseFilter
import javax.ws.rs.ext.Provider

@Provider
class ContextResponseFilter : ContainerResponseFilter {

    @Inject
    lateinit var context: Context

    val log: Logger = Logger.getLogger(this.javaClass)

    //?@kotlin.Throws(IOException::class)
    override fun filter(requestContext: ContainerRequestContext?, responseContext: ContainerResponseContext?) {
        responseContext?.headers?.add(REQUEST_ID, context.requestId)
        MDC.clear()
    }
}