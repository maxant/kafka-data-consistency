package ch.maxant.kdc.mf.library

import io.quarkus.runtime.StartupEvent
import org.eclipse.microprofile.jwt.JsonWebToken
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.context.RequestScoped
import javax.enterprise.event.Observes
import javax.inject.Inject
import javax.validation.ValidationException

@RequestScoped
class Context {
    lateinit var requestId: RequestId // TODO instead of lateinit, always give it a default value, and simply override it in (incoming) boundaries
    var originalMessage: Any? = null
    var command: String? = null
    var event: String? = null
    var demoContext: DemoContext? = null
    var retryCount: Int = 0
    var user: String = "anonymous"
    var jwt: JsonWebToken? = null

    fun getRequestIdSafely() =
        try {
            requestId
        } catch(e: UninitializedPropertyAccessException) {
            requestId = RequestId(UUID.randomUUID().toString()) // can happen e.g. when receiving a message from kafka or doing background work e.g. timers
            requestId
        }

    companion object {
        const val REQUEST_ID = "request-id"
        const val DEMO_CONTEXT = "demo-context"
        const val COMMAND = "command"
        const val EVENT = "event"
        const val RETRY_COUNT = "RETRY_COUNT"

        fun of(requestId: RequestId, originalMessage: Any?, command: String? = null, event: String? = null, demoContext: DemoContext? = null, retryCount: Int = 0): Context {
            val context = Context()
            context.requestId = requestId
            context.originalMessage = originalMessage
            context.command = command
            context.event = event
            context.demoContext = demoContext
            context.retryCount = retryCount
            return context
        }
        fun of(toCopy: Context): Context {
            val context = Context()
            context.requestId = toCopy.getRequestIdSafely()
            context.originalMessage = toCopy.originalMessage
            context.command = toCopy.command
            context.event = toCopy.event
            context.demoContext = toCopy.demoContext
            context.retryCount = toCopy.retryCount
            return context
        }
    }

    fun throwExceptionInContractsIfRequiredForDemo() {
        if(retryCount == 0) {
            val e = demoContext?.forceError?:DemoContext.ForcibleError.none
            if (e == DemoContext.ForcibleError.businessErrorInContracts) {
                throw ValidationException("demo business exception in contracts")
            } else if (e == DemoContext.ForcibleError.technicalErrorInContracts) {
                throw RuntimeException("demo technical exception in contracts")
            }
        }
    }

    fun throwExceptionInPricingIfRequiredForDemo() {
        if(retryCount == 0) {
            val e = demoContext?.forceError?:DemoContext.ForcibleError.none
            if (e == DemoContext.ForcibleError.businessErrorInPricing) {
                throw ValidationException("demo business exception in pricing")
            } else if (e == DemoContext.ForcibleError.technicalErrorInPricing) {
                throw RuntimeException("demo technical exception in pricing")
            }
        }
    }

    fun setup(copy: Context) {
        this.requestId = copy.getRequestIdSafely()
        this.originalMessage = copy.originalMessage
        this.command = copy.command
        this.event = copy.event
        this.demoContext = copy.demoContext
        this.retryCount = copy.retryCount
    }
}

class DemoContext(
        val forceError: ForcibleError?
) {
    var json: String? = null // original, so we dont need the OM to get it again
    enum class ForcibleError {
        none, businessErrorInContracts, technicalErrorInContracts, businessErrorInPricing, technicalErrorInPricing
    }
}

@ApplicationScoped
@SuppressWarnings("unused")
class InitContextForBackgroundProcessing {

    @Inject
    private lateinit var contextInitialisedEvent: javax.enterprise.event.Event<ContextInitialised>

    @Inject
    lateinit var context: Context

    @SuppressWarnings("unused")
    fun setupForBackgroundProcessing(@Observes e: StartupEvent) {
        context.requestId = RequestId(UUID.randomUUID().toString())
        context.command = "BACKGROUND_STARTUP"
        contextInitialisedEvent.fire(object: ContextInitialised {})
    }

}

interface ContextInitialised
