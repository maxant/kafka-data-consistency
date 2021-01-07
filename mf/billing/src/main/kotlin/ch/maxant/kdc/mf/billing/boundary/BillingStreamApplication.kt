package ch.maxant.kdc.mf.billing.boundary

import com.fasterxml.jackson.databind.ObjectMapper
import io.quarkus.runtime.StartupEvent
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StoreQueryParameters
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.GlobalKTable
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.state.KeyValueStore
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Timed
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.Observes
import javax.inject.Inject
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@ApplicationScoped
@Path("/billing")
@Tag(name = "billing")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("unused")
class BillingStreamApplication(
    @Inject
    var om: ObjectMapper,

    @ConfigProperty(name = "kafka.bootstrap.servers")
    val kafkaBootstrapServers: String
) {
    private lateinit var view: ReadOnlyKeyValueStore<String, String>

    fun init(@Observes e: StartupEvent) {
        val props = Properties()
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "mf-billing-streamapplication")
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers)
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String()::class.java)
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String()::class.java)

        val builder = StreamsBuilder()

        val m: Materialized<String, String, KeyValueStore<Bytes, ByteArray>> = Materialized.`as`("billing-state")
        val gkt: GlobalKTable<String, String> = builder.globalTable("billing-internal-state", m)
        val storeName = gkt.queryableStoreName()

        val topology = builder.build()
        println(topology.describe())

        val streams = KafkaStreams(topology, props)

        streams.start()

        val keyValueStore = QueryableStoreTypes.keyValueStore<String, String>()
        view = streams.store(StoreQueryParameters.fromNameAndType(storeName, keyValueStore))

        println(topology.describe())
    }

    @GET
    @Path("/selections")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun getSelections(): Response {
        val selections = mutableListOf<String>()
        // TODO take only those with the prefix "selection::"
        view.all().forEachRemaining { selections.add(it.key) }
        return Response.ok(selections).build()
    }

    @GET
    @Path("/state/{id}")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun getSelection(@Parameter(name = "id") @PathParam("id") id: String): Response {
        return Response.ok(view[id]).build()
    }


}