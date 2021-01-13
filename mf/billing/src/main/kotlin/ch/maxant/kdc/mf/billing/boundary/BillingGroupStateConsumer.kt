package ch.maxant.kdc.mf.billing.boundary

import ch.maxant.kdc.mf.library.KafkaHandler
import ch.maxant.kdc.mf.library.PimpedAndWithDltAndAck
import com.fasterxml.jackson.databind.ObjectMapper
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor
import io.smallrye.mutiny.subscription.MultiEmitter
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.eclipse.microprofile.openapi.annotations.Operation
import org.jboss.logging.Logger
import org.jboss.resteasy.annotations.SseElementType
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.ws.rs.*
import javax.ws.rs.core.MediaType

@ApplicationScoped
@SuppressWarnings("unused")
class BillingGroupStateConsumer : KafkaHandler {

    override fun getKey() = "all-group-state-in"

    override fun getRunInParallel() = true

    val broadcaster = BroadcastProcessor.create<String>()

    @PimpedAndWithDltAndAck
    override fun handle(record: ConsumerRecord<String, String>) {
        broadcaster.onNext(record.value())
    }

}

