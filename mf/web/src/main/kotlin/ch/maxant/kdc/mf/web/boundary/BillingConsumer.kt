package ch.maxant.kdc.mf.web.boundary

import ch.maxant.kdc.mf.library.KafkaHandler
import ch.maxant.kdc.mf.library.PimpedAndWithDltAndAck
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Timed
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject


@ApplicationScoped
@SuppressWarnings("unused")
class BillingConsumer : KafkaHandler {

    @Inject
    lateinit var eventProcessor: EventProcessor

    override fun getKey() = "billing-in"

    override fun getRunInParallel() = true

    @PimpedAndWithDltAndAck
    @Timed(unit = MetricUnits.MILLISECONDS)
    override fun handle(record: ConsumerRecord<String, String>) {
        eventProcessor.process(record)
    }
}
