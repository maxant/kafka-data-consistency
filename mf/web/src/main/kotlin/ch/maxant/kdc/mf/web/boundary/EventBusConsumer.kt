package ch.maxant.kdc.mf.web.boundary

import ch.maxant.kdc.mf.library.Context
import ch.maxant.kdc.mf.library.Context.Companion.DEMO_CONTEXT
import ch.maxant.kdc.mf.library.KafkaHandler
import ch.maxant.kdc.mf.library.PimpedAndWithDltAndAck
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.jboss.logging.Logger
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject


@ApplicationScoped
@SuppressWarnings("unused")
class EventBusConsumer : KafkaHandler {

    @Inject
    lateinit var context: Context

    @Inject
    lateinit var webResource: WebResource

    val log: Logger = Logger.getLogger(this.javaClass)

    override fun getTopic() = "event-bus"

    @PimpedAndWithDltAndAck
    override fun handle(record: ConsumerRecord<String, String>) {
        log.info("handling message for requestId ${context.requestId}")

        var headers = // coz its a string of json that needs its quotes escaping and isnt useful to the web client, as it came from there
                record
                        .headers()
                        .toList()
                        .filter { it.key() != DEMO_CONTEXT }
                        .joinToString { """ "${it.key()}": "${String(it.value())}" """ }
        headers = if(headers.isEmpty()) "" else "$headers,"

        val json = """{ $headers "payload": ${record.value()} }"""

        webResource.sendToSubscribers(context.requestId, json)
    }
}
