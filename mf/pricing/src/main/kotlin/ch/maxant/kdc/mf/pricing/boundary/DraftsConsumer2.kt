package ch.maxant.kdc.mf.pricing.boundary

import ch.maxant.kdc.mf.library.Handler
import org.eclipse.microprofile.reactive.messaging.Message
import java.util.concurrent.CompletionStage
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject

@ApplicationScoped
@SuppressWarnings("unused")
class DraftsConsumer2(
        @Inject
        var draftsConsumer: DraftsConsumer
) : Handler<String> {

    override fun getTopic() = "event-bus"

    override fun handle(msg: Message<String>): CompletionStage<*> {
        return draftsConsumer.process(msg)
    }
}