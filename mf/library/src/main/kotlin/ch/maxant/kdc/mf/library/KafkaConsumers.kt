package ch.maxant.kdc.mf.library

import io.quarkus.arc.Arc
import io.quarkus.runtime.StartupEvent
import org.apache.commons.lang3.mutable.MutableBoolean
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.eclipse.microprofile.config.ConfigProvider
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.context.ManagedExecutor
import org.eclipse.microprofile.context.ThreadContext
import org.jboss.logging.Logger
import org.jboss.resteasy.core.ResteasyContext
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.context.RequestScoped
import javax.enterprise.context.control.ActivateRequestContext
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
        val managedExecutor: ManagedExecutor,

        @Inject
        val pollingLoopRunner: PollingLoopRunner
) {
    @Inject
    lateinit var topicHandlers: Instance<KafkaHandler>

    private val log = Logger.getLogger(this.javaClass)

    private val prefixIncoming = "mf.messaging.incoming."

    private val consumers = mutableListOf<Pair<MutableBoolean, Future<*>>>()

    @PreDestroy
    @SuppressWarnings("unused")
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
            log.info("handing off code for poll loop for $key")
            val f = managedExecutor.supplyAsync {
                log.info("starting up poll loop for $key in 1 sec...")
                Thread.sleep(1000) // wait for container to actually be ready
                log.info("starting up poll loop for $key now")
                pollingLoopRunner.run(topic, props, die, handlers[0])
            }
            consumers.add(die to f)
        }
        log.info("kafka subscriptions setup completed")
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
                .build()
    }

    fun disposeExecutor(@Disposes @WithFreshContext exec: ManagedExecutor) {
        exec.shutdownNow()
    }
}

@ApplicationScoped
class PollingLoopRunner(
        @WithFreshContext
        @Inject
        val managedExecutor: ManagedExecutor
) {
    private val log = Logger.getLogger(this.javaClass)

    @Inject
    lateinit var ensureRequestScopedWorks: EnsureRequestScopedWorksService

    private fun setup() {
        ResteasyContext.pushContextDataMap(HashMap())
        // otherwise an EmtpyMap is used, which causes the rest clients to fail
        // see https://quarkusio.zulipchat.com/#narrow/stream/187030-users/topic/Does.20.40RequestScoped.20only.20work.20with.20web.20requests.3F
    }

    // the following annotation doesnt actually help - debugging, it appears the request context is already acitve anyway :-(
    // see https://quarkusio.zulipchat.com/#narrow/stream/187030-users/topic/Does.20.40RequestScoped.20only.20work.20with.20web.20requests.3F
    @ActivateRequestContext
    fun run(topic: String, props: Properties, die: MutableBoolean, handler: KafkaHandler) {
        setup()
        Thread.currentThread().name = "kfkcnsmr::$topic"
        val consumer = KafkaConsumer<String, String>(props, StringDeserializer(), StringDeserializer())
        consumer.subscribe(listOf(topic))
        log.info("subscribed to $topic")
        var dontBotherClosing = false;
        while(die.isFalse) {
            try {
                log.debug("polling for new records")
                try {
                    pollHandleAndCommit(consumer, handler)
                } catch (e: IllegalStateException) {
                    if("This consumer has already been closed." == e.message) {
                        log.info("consumer is already closed! breaking from polling")
                        dontBotherClosing = true;
                        break
                    }
                    throw e
                }
            } catch (e: Exception) {
                log.error("Failed to process records because of an error. Please ensure that this isnt because of " +
                        "business code and that you use @PimpedAndWithDltAndAck or catch " +
                        "exceptions yourself, rather than letting exceptions be handled by this part of the framework", e)
            } finally {
                log.debug("done with this poll iteration")
            }
        }
        close(consumer, dontBotherClosing)
    }

    private fun pollHandleAndCommit(consumer: KafkaConsumer<String, String>, handler: KafkaHandler) {
        ensureRequestScopedWorks.ensure()
        val records = consumer.poll(Duration.ofSeconds(1)) // so that we don't wait too long for closing
        log.debug("got records: ${records.count()}")
        if (records.count() > 0) {
            log.info("got records: ${records.count()} - dealing with them in parallel: ${handler.runInParallel}")
        }
        val completions = mutableListOf<CompletableFuture<*>>()
        for (record in records) {
            if (handler.runInParallel) {
                completions += managedExecutor.supplyAsync {
                    // TODO in theory, if @ActivateRequestContext does help, we'd probably need to use it here too
                    setup()
                    handler.handle(record)
                }
            } else {
                handler.handle(record)
            }
        }
        completions.forEach { it.get() } // we block because we want to commit them all together

        // default is 60 seconds which blocks this thread. i think, not sure tho, that if it fails, polling simply
        // continues, and we commit next time round. so its not like its super important, esp since everything is
        // built to be idempotent (in theory)
        consumer.commitSync(Duration.ofSeconds(10))

        log.debug("records handled successfully")
    }

    private fun close(consumer: KafkaConsumer<String, String>, dontBotherClosing: Boolean) {
        if(dontBotherClosing) {
            log.info("not bothering to close because of previous exception that indicated that the consumer is already closed")
        } else {
            log.info("closing consumer")
            try {
                consumer.close()
                log.info("closed consumer")
            } catch (e: Exception) {
                log.warn("closing consumer failed: ${e.message}")
            }
        }
    }

}

/** see javadocs below */
@RequestScoped
class RequestScopedState {

    private val log = Logger.getLogger(this.javaClass)

    private var value: String = "0"

    fun getValue() = if(value == "0") { value = Thread.currentThread().name; value} else { value}

    @PostConstruct
    fun init() {
        log.info("created RequestScopedState $this on Thread ${Thread.currentThread().name} with value ${getValue()}")
    }

    @PreDestroy
    fun predestroy() {
        log.info("destroying RequestScopedState $this on Thread ${Thread.currentThread().name} with value ${getValue()}")
    }
}

/**
 * see https://quarkusio.zulipchat.com/#narrow/stream/187030-users/topic/Does.20.40RequestScoped.20only.20work.20with.20web.20requests.3F
 *
 * just to guarantee that request scoped beans work in this scenario, this service is called very so often to check
 * the state is as expected
 */
@ApplicationScoped
class EnsureRequestScopedWorksService {

    private val log = Logger.getLogger(this.javaClass)

    @Inject
    lateinit var state: RequestScopedState

    fun ensure() {
        if(Thread.currentThread().name != state.getValue()) {
            println("KAF001")
            log.error("KAF001 Thread ${Thread.currentThread().name} is using a request scoped bean with state from a different context! (${state.getValue()})")
            Arc.shutdown()
            System.exit(-1)
        }
    }
}
