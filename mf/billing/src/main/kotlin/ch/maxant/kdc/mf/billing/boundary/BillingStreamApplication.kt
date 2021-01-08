package ch.maxant.kdc.mf.billing.boundary

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.quarkus.runtime.StartupEvent
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StoreQueryParameters
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.Aggregator
import org.apache.kafka.streams.kstream.GlobalKTable
import org.apache.kafka.streams.kstream.Initializer
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.state.KeyValueStore
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Timed
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*
import javax.annotation.PreDestroy
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.Observes
import javax.inject.Inject
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import kotlin.collections.HashMap

@ApplicationScoped
@Path("/billing-stream")
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
    private lateinit var jobsView: ReadOnlyKeyValueStore<String, String>
    private lateinit var groupsView: ReadOnlyKeyValueStore<String, String>
    private lateinit var contractsView: ReadOnlyKeyValueStore<String, String>
    private lateinit var streams: KafkaStreams

    fun init(@Observes e: StartupEvent) {
        val props = Properties()
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "mf-billing-streamapplication")
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers)
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String()::class.java)
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String()::class.java)

        val builder = StreamsBuilder()

        val jobsStore: Materialized<String, String, KeyValueStore<Bytes, ByteArray>> = Materialized.`as`("billing-store-jobs")
        val jobsGkt: GlobalKTable<String, String> = builder.globalTable("billing-internal-state-jobs", jobsStore)
        val jobsStoreName = jobsGkt.queryableStoreName()

        val groupsStore: Materialized<String, String, KeyValueStore<Bytes, ByteArray>> = Materialized.`as`("billing-store-groups")
        val groupsGkt: GlobalKTable<String, String> = builder.globalTable("billing-internal-state-groups", groupsStore)
        val groupsStoreName = groupsGkt.queryableStoreName()

        val contractsStore: Materialized<String, String, KeyValueStore<Bytes, ByteArray>> = Materialized.`as`("billing-store-contracts")
        val contractsGkt: GlobalKTable<String, String> = builder.globalTable("billing-internal-state-contracts", contractsStore)
        val contractsStoreName = contractsGkt.queryableStoreName()

        // we do the following, so that we dont have to create locks across JVMs, for updating state. imagine two pods
        // processing two results and updating stats in the job at the same time after both have read the latest state
        // from the GKT. the last one wins and overwrites the data written by the first one.
        // doing it this way with the aggregate, we are guaranteed to have correct data. this is the kafka way of doing
        // things.
        builder.stream<String, String>("billing-internal-events")
                .groupByKey() // { key, value -> om.readTree(value).get("jobId").asText() }
                .aggregate(Initializer { om.writeValueAsString(JobState()) }, jobsAggregator)
                .toStream()
                .to("billing-internal-state-jobs")

        builder.stream<String, String>("billing-internal-events")
                .groupBy { key, value -> om.readTree(value).get("groupId").asText() }
                .aggregate(Initializer { om.writeValueAsString(GroupState()) }, groupsAggregator)
                .toStream()
                .to("billing-internal-state-groups")

        builder.stream<String, String>("billing-internal-events")
                .groupBy { key, value -> om.readTree(value).get("contractId").asText() }
                .aggregate(Initializer { om.writeValueAsString(ContractState()) }, contractsAggregator)
                .toStream()
                .to("billing-internal-state-contracts")

        val topology = builder.build()
        println(topology.describe())

        streams = KafkaStreams(topology, props)

        streams.start()

        jobsView = streams.store(StoreQueryParameters.fromNameAndType(jobsStoreName, QueryableStoreTypes.keyValueStore<String, String>()))
        groupsView = streams.store(StoreQueryParameters.fromNameAndType(groupsStoreName, QueryableStoreTypes.keyValueStore<String, String>()))
        contractsView = streams.store(StoreQueryParameters.fromNameAndType(contractsStoreName, QueryableStoreTypes.keyValueStore<String, String>()))

        println(topology.describe())
    }

    val jobsAggregator = Aggregator {k: String, v: String, j: String ->
        val jobState = om.readValue<JobState>(j)
        val event = om.readValue<Event>(v)
        when(event.action) {
            Action.SENT_TO_PRICING -> {
                jobState.jobId = UUID.fromString(k) // just in case its not set yet
                jobState.groups.computeIfAbsent(event.groupId, k -> State.ST)++
            }
            JobAction.GROUP_SENT_TO_BILLING -> {
                jobState.numGroupsBilling++
            }
            else -> TODO()
        }
        om.writeValueAsString(jobState)
    }

    /* TODO delete this
    fun getJobs(): List<JobState> {
        val jobs = mutableListOf<JobState>()
        jobsView.all().forEachRemaining { jobs.add(om.readValue(it.value)) }
        return jobs
    }
    */

    @PreDestroy
    fun predestroy() {
        streams.close()
    }

    fun getJobState(id: UUID): JobState? {
        val state: String? = jobsView[id.toString()]
        return if(state == null) null else om.readValue(state)
    }

    @GET
    @Path("/job/{id}")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun getJob(@Parameter(name = "id") @PathParam("id") id: UUID): Response {
        return Response.ok(jobsView[id.toString()]).build()
    }

    @GET
    @Path("/group/{id}")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun getGroup(@Parameter(name = "id") @PathParam("id") id: UUID): Response {
        return Response.ok(groupsView[id.toString()]).build()
    }

    @GET
    @Path("/contract/{id}")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun getContract(@Parameter(name = "id") @PathParam("id") id: UUID): Response {
        return Response.ok(contractsView[id.toString()]).build()
    }
}

data class Event(val action: Action, val jobId: UUID, val groupId: UUID, val contractId: UUID, val completed: LocalDateTime? = null)

enum class Action {
    SENT_TO_PRICING, FAILED_IN_PRICING,
    SENT_TO_BILLING, FAILED_IN_BILLING,
    COMPLETED, GROUP_FAILED, FINISHED
}

data class JobState(var jobId: UUID,
                    var state: State,
                    var groups: HashMap<UUID, State>, // 1'000 x 36-chars-UUID + 10-chars-state => less than 100kb?

                    // estimates:

                    var numContractsTotal: Int,
                    var numContractsPricing: Int,
                    var numContractssPricingFailed: Int,
                    var numContractsBilling: Int,
                    var numContractsBillingFailed: Int,
                    var numContractsComplete: Int,
                    var failedContractIds: List<UUID>, // if every contract failed, that'd be 36MB... a litle big!
                    var started: LocalDateTime,
                    var completed: LocalDateTime?
) {
    constructor():
            this(UUID.randomUUID(), State.STARTED, hashMapOf<UUID, State>(), 0, 0, 0, 0, 0, 0, emptyList(), LocalDateTime.now(), null)

}

enum class State {
    STARTED, COMPLETED_PRICING, COMPLETED_BILLING, COMPLETED, FAILED
}

data class GroupState(var jobId: UUID,
                      var groupId: UUID,
                      var state: State,
                      var contracts: HashMap<UUID, State>, // 1'000 x 36-chars-UUID + 10-chars-state => less than 100kb?
                      var started: LocalDateTime,
                      var finished: LocalDateTime?
) {
    constructor():
            this(UUID.randomUUID(), UUID.randomUUID(), State.STARTED, hashMapOf<UUID, State>(), LocalDateTime.now(), null)
}

data class ContractState(var jobId: UUID, var groupId: UUID, var contractId: UUID, var state: State, var contract: Contract) {
    constructor(): this(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), State.STARTED,
            Contract(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), emptyList(), emptyList()))
}
