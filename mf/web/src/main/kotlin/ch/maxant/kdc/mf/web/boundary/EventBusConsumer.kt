package ch.maxant.kdc.mf.web.boundary

import ch.maxant.kdc.mf.library.Context
import ch.maxant.kdc.mf.library.Context.Companion.DEMO_CONTEXT
import ch.maxant.kdc.mf.library.KafkaHandler
import ch.maxant.kdc.mf.library.PimpedAndWithDltAndAck
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Timed
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
    @Timed(unit = MetricUnits.MILLISECONDS)
    override fun handle(record: ConsumerRecord<String, String>) {
        var headers = // coz its a string of json that needs its quotes escaping and isnt useful to the web client, as it came from there
                record
                    .headers()
                    .toList()

        val requestId = headers
                .filter { it.key() == Context.REQUEST_ID }
                .map { String(it.value()) }
                .firstOrNull()?:context.getRequestIdSafely().toString()

        log.info("handling message for requestId $requestId")

        var headers2 = headers
                        .filter { it.key() != DEMO_CONTEXT }
                        .joinToString { """ "${it.key()}": "${String(it.value())}" """ }

        headers2 = if(headers2.isEmpty()) "" else "$headers2,"

        val json = """{ $headers2 "payload": ${record.value()} }"""

        webResource.sendToSubscribers(requestId, json)
    }
}
