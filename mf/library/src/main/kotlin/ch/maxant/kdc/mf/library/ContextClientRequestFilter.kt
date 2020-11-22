package ch.maxant.kdc.mf.library

import ch.maxant.kdc.mf.library.Context.Companion.REQUEST_ID
import org.jboss.logging.Logger
import javax.inject.Inject
import javax.ws.rs.client.ClientRequestContext
import javax.ws.rs.client.ClientRequestFilter
import javax.ws.rs.ext.Provider

@Provider
class ContextClientRequestFilter : ClientRequestFilter {

    @Inject
    lateinit var context: Context

    val log: Logger = Logger.getLogger(this.javaClass)

    override fun filter(requestContext: ClientRequestContext) {
        log.info("adding request id")
        requestContext.headers.add(REQUEST_ID, context.requestId.toString())
    }
}