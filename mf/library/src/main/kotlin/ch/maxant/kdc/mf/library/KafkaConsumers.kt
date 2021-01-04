package ch.maxant.kdc.mf.library

import io.quarkus.runtime.StartupEvent
import io.smallrye.reactive.messaging.kafka.IncomingKafkaRecord
import io.smallrye.reactive.messaging.kafka.commit.KafkaIgnoreCommit
import io.smallrye.reactive.messaging.kafka.fault.KafkaIgnoreFailure
import io.vertx.kafka.client.consumer.impl.KafkaConsumerRecordImpl
import io.vertx.mutiny.kafka.client.consumer.KafkaConsumerRecord
import org.apache.commons.lang3.mutable.MutableBoolean
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.eclipse.microprofile.config.ConfigProvider
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.context.ManagedExecutor
import org.eclipse.microprofile.context.ThreadContext
import org.eclipse.microprofile.reactive.messaging.Message
import org.jboss.logging.Logger
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import javax.annotation.PreDestroy
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.Observes
import javax.enterprise.inject.Disposes
import javax.enterprise.inject.Instance
import javax.enterprise.inject.Produces
import javax.inject.Inject

@ApplicationScoped
@SuppressWarnings("unused")
class KafkaConsumers(
        @ConfigProperty(name = "kafka.bootstrap.servers")
        val kafkaBootstrapServers: String,

        @WithFreshContext
        @Inject
        val managedExecutor: ManagedExecutor
) {
    @Inject
    lateinit var topicHandlers: Instance<KafkaHandler>

    private val log = Logger.getLogger(this.javaClass)

    private val prefixIncoming = "mf.messaging.incoming."

    private val consumers = mutableListOf<Pair<MutableBoolean, Future<*>>>()

    @PreDestroy
    fun destroy() {
        consumers.forEach { it.first.setTrue() }
        consumers.forEach { it.second.get() }
        consumers.clear()
    }

    fun init(@Observes e: StartupEvent) {
        val config = ConfigProvider.getConfig()
        val keys = config.propertyNames
                .filter { it.startsWith(prefixIncoming) }
                .map { it.substring(prefixIncoming.length) }
                .map { it.substring(0, it.indexOf(".")) }
                .distinct()

        log.info("creating kafka consumers for these keys: $keys")

        for (key in keys) {
            val props = Properties()
            props["bootstrap.servers"] = kafkaBootstrapServers
            props["group.id"] = config.getValue("$prefixIncoming$key.group.id", String::class.java)
            val autoOffsetReset = config.getOptionalValue("$prefixIncoming$key.auto.offset.reset", String::class.java)
            if(autoOffsetReset.isPresent) {
                props["auto.offset.reset"] = autoOffsetReset.get()
            }
            props["enable.auto.commit"] = "false"
            //props["enable.auto.commit"] = "true"
            //props["auto.commit.interval.ms"] = "1000"
            val topic = config.getValue("$prefixIncoming$key.topic", String::class.java)
            val handlers = topicHandlers.filter { it.key == key }
            if(handlers.isEmpty()) throw IllegalArgumentException("No topic handler configured for topic '$topic'")
            if(handlers.size > 1) throw IllegalArgumentException("More than one topic handler configured for topic '$topic'")

            val die = MutableBoolean(false)
            val f = managedExecutor.supplyAsync {
                Thread.currentThread().name = "kfkcnsmr::$topic"
                val consumer = KafkaConsumer<String, String>(props, StringDeserializer(), StringDeserializer())
                consumer.subscribe(listOf(topic))
                log.info("subscribed to $topic")
                run(die, consumer, handlers[0])
            }
            consumers.add(die to f)
        }
        log.info("kafka subscriptions setup completed")
    }

    private fun run(die: MutableBoolean, consumer: KafkaConsumer<String, String>, handler: KafkaHandler) {
        while(die.isFalse) {
            try {
                log.debug("polling for new records")
                try {
                    val records = consumer.poll(Duration.ofSeconds(1)) // so that we don't wait too long for closing
                    log.debug("got records: ${records.count()}")
                    if(records.count() > 0) {
                        log.info("got records: ${records.count()}")
                    }
                    val completions = mutableListOf<CompletableFuture<*>>()
                    for (record in records) {
                        if(handler.runInParallel) {
                            completions += managedExecutor.supplyAsync { handler.handle(record) }
                        } else {
                            handler.handle(record)
                        }
                    }
                    completions.forEach { it.get() } // we block because we want to commit them all together
                    consumer.commitSync()
                    log.debug("records handled successfully")
                } catch (e: IllegalStateException) {
                    if("This consumer has already been closed." == e.message) {
                        log.info("consumer is already closed! breaking from polling")
                        break
                    }
                    throw e
                }
            } catch (e: Exception) {
                log.error("Failed to process records because of an error. Records will be skipped. " +
                        "This should not happen, because you should catch exceptions in your business code or let them " +
                        "be handled using @PimpedAndWithDltAndAck", e)
            } finally {
                log.debug("done with this poll loop, going round again")
            }
        }
        log.info("closing consumer")
        try {
            consumer.close()
            log.info("closed consumer")
        } catch (e: Exception) {
            log.warn("closing consumer failed: ${e.message}")
        }
    }

    private fun toMessage(record: ConsumerRecord<String, String>?): Message<String> {
        val r = KafkaConsumerRecord<String, String>(KafkaConsumerRecordImpl(record))
        return IncomingKafkaRecord(r, KafkaIgnoreCommit(), KafkaIgnoreFailure("TODO"), false, false)
    }
}

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class WithFreshContext

@ApplicationScoped
class ManagedExecutorWithFreshContextProducer {

    @Produces @ApplicationScoped @WithFreshContext
    fun createExecutor(): ManagedExecutor {
        return ManagedExecutor.builder()
                .propagated(*ThreadContext.NONE)
                .cleared(ThreadContext.ALL_REMAINING)
                .build();
    }

    fun disposeExecutor(@Disposes @WithFreshContext exec: ManagedExecutor) {
        exec.shutdownNow();
    }
}
