package ch.maxant.kdc.mf.library

import javax.enterprise.context.RequestScoped

@RequestScoped
class Context {
    lateinit var requestId: RequestId
    var originalMessage: Any? = null
    var command: String? = null
    var event: String? = null

    companion object {
        fun of(requestId: RequestId, originalMessage: Any?, command: String? = null, event: String? = null): Context {
            val context = Context()
            context.requestId = requestId
            context.originalMessage = originalMessage
            context.command = command
            context.event = event
            return context
        }
    }
}