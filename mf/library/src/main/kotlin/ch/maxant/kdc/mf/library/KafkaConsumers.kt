package ch.maxant.kdc.mf.library

import io.quarkus.runtime.StartupEvent
import io.smallrye.reactive.messaging.kafka.IncomingKafkaRecord
import io.smallrye.reactive.messaging.kafka.commit.KafkaIgnoreCommit
import io.smallrye.reactive.messaging.kafka.fault.KafkaIgnoreFailure
import io.vertx.kafka.client.consumer.impl.KafkaConsumerRecordImpl
import io.vertx.mutiny.kafka.client.consumer.KafkaConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.eclipse.microprofile.config.ConfigProvider
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.context.ThreadContext
import org.eclipse.microprofile.reactive.messaging.Message
import org.jboss.logging.Logger
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletionStage
import java.util.concurrent.CountDownLatch
import javax.annotation.PreDestroy
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.Observes
import javax.enterprise.inject.Instance
import javax.inject.Inject

@ApplicationScoped
@SuppressWarnings("unused")
class KafkaConsumers(
        @ConfigProperty(name = "kafka.bootstrap.servers")
        val kafkaBootstrapServers: String,

        @Inject
        val threadContext: ThreadContext
) {

    @Inject
    lateinit var topicHandlers: Instance<Handler<*>>

    private val log = Logger.getLogger(this.javaClass)

    private val prefixIncoming = "mf.messaging.incoming."

    private val consumers = mutableListOf<KafkaConsumer<String, String>>()
    private val consumersToClose = mutableListOf<Pair<KafkaConsumer<String, String>, CountDownLatch>>()

    @PreDestroy
    fun down() {
        consumersToClose.addAll(consumers.map { it.to(CountDownLatch(1)) })
        consumersToClose.forEach { it.second.await() } // wait for them to all close on their respective thread
        consumersToClose.clear()
        consumers.clear()
    }

    fun init(@Observes e: StartupEvent) {
        val config = ConfigProvider.getConfig()
        val configs = config.propertyNames
                .filter { it.startsWith(prefixIncoming) }
                .map { it.substring(prefixIncoming.length) }
                .map { it.substring(0, it.indexOf(".")) }
                .distinct()

        log.info("creating kafka consumers for these configs: $configs")

        for (cg in configs) {
            val props = Properties()
            props["bootstrap.servers"] = kafkaBootstrapServers
            props["group.id"] = config.getValue("$prefixIncoming$cg.group.id", String::class.java)
            val autoOffsetReset = config.getOptionalValue("$prefixIncoming$cg.auto.offset.reset", String::class.java)
            if(autoOffsetReset.isPresent) {
                props["auto.offset.reset"] = autoOffsetReset.get()
            }
            props["enable.auto.commit"] = "true"
            props["auto.commit.interval.ms"] = "1000"
            val topic = config.getValue("$prefixIncoming$cg.topic", String::class.java)
            val handlers = topicHandlers.filter { it.getTopic() == topic }
            if(handlers.isEmpty()) throw IllegalArgumentException("No topic handler configured for topic '$topic'")
            if(handlers.size > 1) throw IllegalArgumentException("More than one topic handler configured for topic '$topic'")

            val t = Thread(threadContext.contextualRunnable {
                val consumer = KafkaConsumer<String, String>(props, StringDeserializer(), StringDeserializer())
                consumers.add(consumer)
                consumer.subscribe(listOf(topic))
                log.info("subscribed to $topic")
                run(consumer, handlers[0])
            })
            t.isDaemon = true
            t.name = "$topic::consumer"
            t.start()
        }
        log.info("kafka subscriptions setup completed")
    }

    private fun <V> run(consumer: KafkaConsumer<*,*>, handler: Handler<V>) {
        while(true) {
            try {
                if(consumersToClose.any { it.first == consumer }) {
                    log.info("closing consumer")
                    consumer.close()
                    consumersToClose.filter { it.first == consumer }.forEach { it.second.countDown() } // signal that we're done
                    log.info("closed and informed")
                    break
                } else {
                    log.info("polling for new records")
                    val records = consumer.poll(Duration.ofMinutes(1))
                    log.info("got records: ${records.count()}")
                    for (record in records) {
                        handler.handle(toMessage(record) as Message<V>) //.toCompletableFuture().get()
                    }
                    log.info("records handled successfully")
                }
            } catch (e: Exception) {
                log.error("Failed to process records because of an error. Records will be skipped. " +
                        "This should not happen, because you should catch exceptions in your business code or let them " +
                        "be handled using @PimpedAndWithDltAndAck", e)
            } finally {
                log.info("done with this poll loop")
            }
        }
    }

    private fun toMessage(record: ConsumerRecord<out Any, out Any>?): Message<Any> {
        val r = KafkaConsumerRecord<Any,Any>(KafkaConsumerRecordImpl(record))
        return IncomingKafkaRecord(r, KafkaIgnoreCommit(), KafkaIgnoreFailure("TODO"), false, false)
    }
}

interface Handler<V> {
    fun handle(message: Message<V>): CompletionStage<*>
    fun getTopic(): String
}
