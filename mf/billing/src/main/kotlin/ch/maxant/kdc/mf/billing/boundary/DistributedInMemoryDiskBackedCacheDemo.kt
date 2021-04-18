package ch.maxant.kdc.mf.billing.boundary

import ch.maxant.kdc.mf.library.Context.Companion.EVENT
import io.quarkus.runtime.StartupEvent
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.*
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.Named
import org.apache.kafka.streams.kstream.Transformer
import org.apache.kafka.streams.kstream.TransformerSupplier
import org.apache.kafka.streams.processor.ProcessorContext
import org.apache.kafka.streams.state.KeyValueStore
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Timed
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import org.jboss.logging.Logger
import java.util.*
import javax.annotation.PreDestroy
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.Observes
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@ApplicationScoped
@Path("/billing-contract-cache")
@Tag(name = "billing-contract-cache")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("unused")
class DistributedInMemoryDiskBackedCacheDemo(
    @ConfigProperty(name = "kafka.bootstrap.servers")
    val kafkaBootstrapServers: String
) {
    private lateinit var contractsView: ReadOnlyKeyValueStore<String, String>
    private lateinit var streams: KafkaStreams
    private val globalStoreName = "contracts-cache"
    private val globalStore: Materialized<String, String, KeyValueStore<Bytes, ByteArray>> = Materialized.`as`(globalStoreName)
    private val log = Logger.getLogger(this.javaClass)

    @SuppressWarnings("unused")
    fun init(@Observes e: StartupEvent) {

        val props = Properties()
        props[StreamsConfig.APPLICATION_ID_CONFIG] = "mf-billing-contractcacheapplication"
        props[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaBootstrapServers
        props[StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG] = Serdes.String()::class.java
        props[StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG] = Serdes.String()::class.java
        props[StreamsConfig.COMMIT_INTERVAL_MS_CONFIG] = 100 // default is 30_000 for at_least_once
        val builder = StreamsBuilder()

        val contractsCacheTopicName = "billing-internal-contracts-cache"

        builder
            .stream<String, String>("contracts-event-bus")
            .transform(TransformerSupplier<String, String, KeyValue<String, ContractAndEvent>>{
                ContractTransformer()
            })
            .peek {k, _ -> log.info("transformed event $k")}
            .filter( { _, v -> v.event == "APPROVED_CONTRACT" }, Named.`as`("billing-internal-contracts-cache-filter"))
            .map( { k, v -> KeyValue(k, v.contract) }, Named.`as`("billing-internal-contracts-cache-map"))
            .peek {k, v -> log.info("filtered event $k: $v")}
            .to(contractsCacheTopicName)

        builder.globalTable(contractsCacheTopicName, globalStore)

        val topology = builder.build()
        println(topology.describe())

        streams = KafkaStreams(topology, props)

        streams.start()

        println(topology.describe())
    }

    @PreDestroy
    @SuppressWarnings("unused")
    fun predestroy() {
        streams.close()
    }

    private fun getContract(key: String) =
        try {
            contractsView.get(key)
        } catch (e: UninitializedPropertyAccessException) {
            contractsView = streams.store(
                StoreQueryParameters.fromNameAndType(globalStoreName, QueryableStoreTypes.keyValueStore<String, String>())
            )
            contractsView.get(key)
        }

    @GET
    @Path("/contract/{id}")
    @Operation(summary = "NOTE: knows about every contract!")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun getContract(@Parameter(name = "id") @PathParam("id") id: UUID): Response {
        return Response.ok(getContract(id.toString())).build()
    }
}

class ContractTransformer : Transformer<String, String, KeyValue<String, ContractAndEvent>> {
    private lateinit var context: ProcessorContext

    override fun init(context: ProcessorContext) {
        this.context = context
    }

    override fun transform(key: String, value: String): KeyValue<String, ContractAndEvent> {
        val eventArray = context.headers().toArray().filter { it.key().equals(EVENT) }
        val event = if(eventArray.isNotEmpty()) {
            String(eventArray[0].value())
        } else ""
        return KeyValue(key, ContractAndEvent(value, event))
    }

    override fun close() {
    }
}

data class ContractAndEvent(val contract: String, val event: String)