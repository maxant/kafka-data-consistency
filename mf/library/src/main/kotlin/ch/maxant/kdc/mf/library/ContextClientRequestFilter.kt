package ch.maxant.kdc.mf.library

import ch.maxant.kdc.mf.library.Context.Companion.REQUEST_ID
import org.jboss.logging.Logger
import java.util.*
import javax.inject.Inject
import javax.ws.rs.client.ClientRequestContext
import javax.ws.rs.client.ClientRequestFilter
import javax.ws.rs.ext.Provider

/**
 * sits between rest adapters and the network, and ensures the context is copied across
 */
@Provider
class ContextClientRequestFilter : ClientRequestFilter {

    @Inject
    lateinit var context: Context

    val log: Logger = Logger.getLogger(this.javaClass)

    override fun filter(requestContext: ClientRequestContext) {
        val requestId = context.getRequestIdSafely()
        log.debug("adding requestId $requestId to outbound rest request")
        requestContext.headers.add(REQUEST_ID, requestId.toString())
    }
}