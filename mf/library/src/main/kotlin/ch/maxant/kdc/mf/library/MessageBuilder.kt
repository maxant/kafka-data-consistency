package ch.maxant.kdc.mf.library

import com.fasterxml.jackson.databind.ObjectMapper
import io.smallrye.reactive.messaging.kafka.IncomingKafkaRecordMetadata
import org.apache.commons.lang3.exception.ExceptionUtils
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.reactive.messaging.Message
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.CompletionStage
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.context.RequestScoped
import javax.inject.Inject

@ApplicationScoped
class MessageBuilder {

    @Inject
    lateinit var context: Context

    @Inject
    lateinit var om: ObjectMapper

    /**
     * Build the message, with header propagation
     */
    fun build(key: Any?,
              value: Any,
              ack: CompletableFuture<Unit>,
              command: String? = null,
              event: String? = null) =

        messageWithMetadata(key?.toString(), om.writeValueAsString(value), Headers(
                    context,
                    command,
                    event
            ),
            ack
        )
}
