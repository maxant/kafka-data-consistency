package ch.maxant.kdc.mf.web.boundary

import ch.maxant.kdc.mf.library.Context
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.jboss.logging.Logger
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject

@ApplicationScoped
class EventProcessor {

    @Inject
    lateinit var context: Context

    @Inject
    lateinit var webResource: WebResource

    val log: Logger = Logger.getLogger(this.javaClass)

    fun process(record: ConsumerRecord<String, String>) {
        val headers = // coz its a string of json that needs its quotes escaping and isnt useful to the web client, as it came from there
            record
                .headers()
                .toList()

        val sessionId = headers
            .filter { it.key() == Context.SESSION_ID }
            .map { String(it.value()) }
            .firstOrNull()?:context.getSessionIdSafely().toString()

        log.info("handling message for sessionId $sessionId")

        var headers2 = headers
            .filter { it.key() != Context.DEMO_CONTEXT }
            .joinToString { """ "${it.key()}": "${String(it.value())}" """ }

        headers2 = if(headers2.isEmpty()) "" else "$headers2,"

        val json = """{ $headers2 "payload": ${record.value()} }"""

        webResource.sendToSubscribers(sessionId, json, record.key())
    }
}
