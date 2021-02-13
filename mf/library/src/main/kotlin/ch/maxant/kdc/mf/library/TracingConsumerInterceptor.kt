package ch.maxant.kdc.mf.library

import io.opentracing.contrib.kafka.TracingKafkaUtils
import io.opentracing.util.GlobalTracer
import org.apache.kafka.clients.consumer.ConsumerRecords

class TracingConsumerInterceptor<K, V> : io.opentracing.contrib.kafka.TracingConsumerInterceptor<K, V>() {
    override fun onConsume(records: ConsumerRecords<K, V>): ConsumerRecords<K, V>? {
        for (record in records) {
            TracingKafkaUtils.buildAndFinishChildSpan(record, GlobalTracer.get())
        }
        return records
    }
}
