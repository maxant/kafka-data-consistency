package ch.maxant.kdc.mf.billing.boundary

import ch.maxant.kdc.mf.library.KafkaHandler
import ch.maxant.kdc.mf.library.PimpedAndWithDltAndAck
import org.apache.kafka.clients.consumer.ConsumerRecord
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject

@ApplicationScoped
@SuppressWarnings("unused")
class BillingJobStateConsumer : KafkaHandler {

    override fun getKey() = "all-job-state-in"

    override fun getRunInParallel() = true

    @Inject
    lateinit var billingStreamApplication: BillingStreamApplication

    @PimpedAndWithDltAndAck
    override fun handle(record: ConsumerRecord<String, String>) {
        billingStreamApplication.sendToSubscribers(record.value())
    }

}

