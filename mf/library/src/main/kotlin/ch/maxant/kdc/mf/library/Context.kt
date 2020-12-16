package ch.maxant.kdc.mf.library

import io.quarkus.runtime.StartupEvent
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.context.RequestScoped
import javax.enterprise.event.Observes
import javax.inject.Inject
import javax.validation.ValidationException

@RequestScoped
class Context {
    lateinit var requestId: RequestId
    var originalMessage: Any? = null
    var command: String? = null
    var event: String? = null
    var demoContext: DemoContext? = null
    var retryCount: Int = 0

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
            try {
                context.requestId = toCopy.requestId
            } catch(e: UninitializedPropertyAccessException) {
                context.requestId = RequestId(UUID.randomUUID().toString()) // can happen when receiving a message from kafka
            }
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
class InitContextForBackgroundProcessing {

    @Inject
    private lateinit var contextInitialisedEvent: javax.enterprise.event.Event<ContextInitialised>

    @Inject
    lateinit var context: Context

    fun setupForBackgroundProcessing(@Observes e: StartupEvent) {
        context.requestId = RequestId(UUID.randomUUID().toString())
        context.command = "BACKGROUND_STARTUP"
        contextInitialisedEvent.fire(object: ContextInitialised {})
    }

}

interface ContextInitialised
