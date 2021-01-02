package ch.maxant.kdc.mf.web.boundary

import ch.maxant.kdc.mf.library.Handler
import org.eclipse.microprofile.reactive.messaging.Message
import java.util.concurrent.CompletionStage
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject

@ApplicationScoped
@SuppressWarnings("unused")
class WebResource2(
        @Inject
        var webResource: WebResource
) : Handler<String> {

    override fun getTopic() = "event-bus"

    override fun handle(msg: Message<String>): CompletionStage<*> {
        return webResource.processEventBus(msg)
    }
}