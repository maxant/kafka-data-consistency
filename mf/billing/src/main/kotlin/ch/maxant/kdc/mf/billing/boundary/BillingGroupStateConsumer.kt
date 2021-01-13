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
@Path("/billing-state")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("unused")
class BillingGroupStateConsumer : KafkaHandler {

    override fun getKey() = "all-group-state-in"

    override fun getRunInParallel() = true

    val broadcaster = BroadcastProcessor.create<String>()

    @PimpedAndWithDltAndAck
    override fun handle(record: ConsumerRecord<String, String>) {
        broadcaster.onNext(record.value())
    }

    @GET
    @Path("/subscribe-to-all-jobs")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @SseElementType(MediaType.APPLICATION_JSON)
    @Operation(summary = "get notification of changes to all groups of any job that is currently running")
    fun stream() = broadcaster

}
