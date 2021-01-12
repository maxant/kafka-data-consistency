package ch.maxant.kdc.mf.billing.control

import ch.maxant.kdc.mf.billing.boundary.Group
import com.fasterxml.jackson.databind.ObjectMapper
import io.smallrye.reactive.messaging.kafka.OutgoingKafkaRecordMetadata
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.reactive.messaging.Message
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject

@ApplicationScoped
@SuppressWarnings("unused")
class StreamService(
        @Inject
        var om: ObjectMapper
) {
    @Inject
    @Channel("stream-out")
    lateinit var stream: Emitter<String>

    fun sendGroup(group: Group) {
        val md = OutgoingKafkaRecordMetadata.builder<String>()
                .withKey(group.jobId.toString())
                .build()
        val msg = Message.of(om.writeValueAsString(group))
        stream.send(msg.addMetadata(md))
    }
}
