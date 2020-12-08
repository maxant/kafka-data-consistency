package ch.maxant.kdc.mf.library

import ch.maxant.kdc.mf.library.Context.Companion.REQUEST_ID
import org.jboss.logging.Logger
import java.util.*
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
        val requestId = try {
            context.requestId
        } catch (e: UninitializedPropertyAccessException) {
            // ignore - this can happen when eg fetching security info at startup ie when no user is involved
            RequestId(UUID.randomUUID().toString())
        }
        log.debug("adding requestId $requestId to outbound rest request")
        requestContext.headers.add(REQUEST_ID, requestId.toString())
    }
}