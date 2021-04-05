package ch.maxant.kdc.mf.library

import ch.maxant.kdc.mf.library.Context.Companion.REQUEST_ID
import ch.maxant.kdc.mf.library.Context.Companion.SESSION_ID
import io.opentracing.contrib.kafka.ClientSpanNameProvider
import io.opentracing.contrib.kafka.SpanDecorator
import io.opentracing.contrib.kafka.TracingKafkaUtils
import io.opentracing.util.GlobalTracer
import org.apache.kafka.clients.producer.ProducerRecord

class TracingProducerInterceptor<K, V> : io.opentracing.contrib.kafka.TracingProducerInterceptor<K, V>() {
    override fun onSend(producerRecord: ProducerRecord<K, V>): ProducerRecord<K, V> {
        val parent = TracingKafkaUtils.extractSpanContext(producerRecord.headers(), GlobalTracer.get())
        val span = TracingKafkaUtils.buildAndInjectSpan(
            producerRecord,
            GlobalTracer.get(),
            ClientSpanNameProvider.PRODUCER_OPERATION_NAME,
            parent,
            listOf(SpanDecorator.STANDARD_TAGS)
        )

        val requestId = String(producerRecord
            .headers()
            ?.find { it.key() == REQUEST_ID }
            ?.value()
            ?: byteArrayOf(),
            Charsets.UTF_8)
        span.setTag(REQUEST_ID, requestId)

        val sessionId = String(producerRecord
            .headers()
            ?.find { it.key() == SESSION_ID }
            ?.value()
            ?: byteArrayOf(),
            Charsets.UTF_8)
        span.setTag(SESSION_ID, sessionId)

        span.finish()

        return producerRecord
    }
}
