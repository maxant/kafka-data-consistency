package ch.maxant.kdc.mf.billing.boundary

import ch.maxant.kdc.mf.library.*
import com.fasterxml.jackson.databind.ObjectMapper
import io.quarkus.runtime.StartupEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.eclipse.microprofile.config.ConfigProvider
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.jboss.logging.Logger
import java.util.*
import java.util.concurrent.CompletableFuture
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.Observes
import javax.enterprise.event.TransactionPhase
import javax.inject.Inject

@ApplicationScoped
@SuppressWarnings("unused")
class BillingStreamApplication(
    @Inject
    var om: ObjectMapper,

    @ConfigProperty(name = "kafka.bootstrap.servers")
    val kafkaBootstrapServers: String
) {

    fun init(@Observes e: StartupEvent) {
        val props = Properties()
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "streams-pipe")
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers)
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String()::class.java)
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String()::class.java)

        val builder = StreamsBuilder()

        builder.globalTable()

        val source: KStream<String, String> = builder.stream("billing-internal-state")

        val topology = builder.build()
        println(topology.describe())
        println(topology.describe())
    }
}