package ch.maxant.kdc.mf.library

import com.fasterxml.jackson.databind.ObjectMapper
import java.util.concurrent.CompletableFuture
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject

@ApplicationScoped
class MessageBuilder {

    @Inject
    lateinit var context: Context

    @Inject
    lateinit var om: ObjectMapper

    /**
     * Build the message, with header propagation
     *
     * TODO replace all usages of this!
     */
    @Deprecated(message = "make a new one that doesnt take a CF")
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

    fun build(key: Any?,
              value: Any,
              command: String? = null,
              event: String? = null) =

        messageWithMetadata(key?.toString(), om.writeValueAsString(value), Headers(
                    context,
                    command,
                    event
            )
        )
}
