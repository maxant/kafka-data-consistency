package ch.maxant.kdc.mf.library

import com.fasterxml.jackson.databind.ObjectMapper
import io.opentracing.Tracer
import java.util.concurrent.CompletableFuture
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject

@ApplicationScoped
class MessageBuilder {

    @Inject
    lateinit var context: Context

    @Inject
    lateinit var om: ObjectMapper

    @Inject
    lateinit var tracer: Tracer

    /**
     * Build the message, with header propagation
     */
    fun build(key: Any?,
              value: Any,
              command: String? = null,
              event: String? = null) =

        messageWithMetadata(key?.toString(), om.writeValueAsString(value), Headers(
                    context,
                    command,
                    event
            ),
            tracer
        )
}
