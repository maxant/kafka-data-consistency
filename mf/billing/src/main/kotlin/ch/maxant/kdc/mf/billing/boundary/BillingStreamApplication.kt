package ch.maxant.kdc.mf.billing.boundary

import ch.maxant.kdc.mf.billing.control.Action
import ch.maxant.kdc.mf.billing.control.Event
import ch.maxant.kdc.mf.billing.control.EventService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.quarkus.runtime.StartupEvent
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StoreQueryParameters
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.*
import org.apache.kafka.streams.state.KeyValueStore
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Timed
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import java.time.LocalDateTime
import java.util.*
import javax.annotation.PreDestroy
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.Observes
import javax.inject.Inject
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@ApplicationScoped
@Path("/billing-stream")
@Tag(name = "billing")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("unused")
class BillingStreamApplication(
    @Inject
    var om: ObjectMapper,

    @Inject
    var eventService: EventService,

    @ConfigProperty(name = "kafka.bootstrap.servers")
    val kafkaBootstrapServers: String
) {
    private lateinit var jobsView: ReadOnlyKeyValueStore<String, String>

    private lateinit var groupsView: ReadOnlyKeyValueStore<String, String>

    private lateinit var contractsView: ReadOnlyKeyValueStore<String, String>

    private lateinit var streams: KafkaStreams

    @SuppressWarnings("unused")
    fun init(@Observes e: StartupEvent) {
        val props = Properties()
        props[StreamsConfig.APPLICATION_ID_CONFIG] = "mf-billing-streamapplication"
        props[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaBootstrapServers
        props[StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG] = Serdes.String()::class.java
        props[StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG] = Serdes.String()::class.java

        val builder = StreamsBuilder()

        val events = builder.stream<String, String>("billing-internal-events")

        // we do the following, so that we dont have to create locks across JVMs, for updating state. imagine two pods
        // processing two results and updating stats in the job at the same time after both have read the latest state
        // from the GKT. the last one wins and overwrites the data written by the first one.
        // doing it this way with the aggregate, we are guaranteed to have correct data. this is the kafka way of doing
        // things.

        // JOBS
        events.groupByKey() // { key, value -> om.readTree(value).get("jobId").asText() }
                .aggregate(Initializer { om.writeValueAsString(JobState()) }, jobsAggregator,
                        Named.`as`("billing-internal-aggregate-state-jobs"), Materialized.with(Serdes.String(), Serdes.String()))
                .toStream(Named.`as`("billing-internal-stream-state-jobs"))
                .to("billing-internal-state-jobs")

        val jobsStore: Materialized<String, String, KeyValueStore<Bytes, ByteArray>> = Materialized.`as`("billing-store-jobs")
        val jobsGkt: GlobalKTable<String, String> = builder.globalTable("billing-internal-state-jobs", jobsStore)
        val jobsStoreName = jobsGkt.queryableStoreName()

        // GROUPS
        events.groupBy { _, value -> om.readTree(value).get("groupId").asText() }
                .aggregate(Initializer { om.writeValueAsString(GroupState()) }, groupsAggregator,
                        Named.`as`("billing-internal-aggregate-state-groups"), Materialized.with(Serdes.String(), Serdes.String()))
                .toStream(Named.`as`("billing-internal-stream-state-groups"))
                .to("billing-internal-state-groups")

        val groupsStore: Materialized<String, String, KeyValueStore<Bytes, ByteArray>> = Materialized.`as`("billing-store-groups")
        val groupsGkt: GlobalKTable<String, String> = builder.globalTable("billing-internal-state-groups", groupsStore)
        val groupsStoreName = groupsGkt.queryableStoreName()

        // CONTRACTS
        events.groupBy { _, value ->
                    val tree = om.readTree(value)
                    // a contract can be in 1..n groups (as if there is a failure, it will be sent again, on its own
                    // and a contract can be priced by many jobs. so we need a key with more than just the contractId,
                    // but can't use the groupId => jobId+contractId is suitable
                    tree.get("jobId").asText() + "::" + tree.get("contractId").asText()
                }.aggregate(Initializer { om.writeValueAsString(ContractState()) }, contractsAggregator,
                        Named.`as`("billing-internal-aggregate-state-contracts"), Materialized.with(Serdes.String(), Serdes.String()))
                .toStream(Named.`as`("billing-internal-stream-state-contracts"))
                .to("billing-internal-state-contracts")

        val contractsStore: Materialized<String, String, KeyValueStore<Bytes, ByteArray>> = Materialized.`as`("billing-store-contracts")
        val contractsGkt: GlobalKTable<String, String> = builder.globalTable("billing-internal-state-contracts", contractsStore)
        val contractsStoreName = contractsGkt.queryableStoreName()

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
                jobState.jobId = event.jobId // just in case its not set yet

                jobState.groups.computeIfAbsent(event.groupId) { State.STARTED }
                jobState.numContractsTotal++
                jobState.numContractsPricing++
            }
            Action.FAILED_IN_PRICING -> {
                jobState.groups.computeIfAbsent(event.groupId) { State.FAILED }
                jobState.state = State.FAILED
                jobState.numContractsPricingFailed++
            }
            Action.SENT_TO_BILLING -> {
                jobState.numContractsBilling++
            }
            Action.FAILED_IN_BILLING -> {
                jobState.groups.computeIfAbsent(event.groupId) { State.FAILED }
                jobState.state = State.FAILED
                jobState.numContractsBillingFailed++
            }
            Action.COMPLETED -> {
                // nothing to do when a contract completes, because we dont know about individual contracts at the job level!
            }
            Action.COMPLETED_GROUP -> {
                jobState.groups.computeIfAbsent(event.groupId) { State.COMPLETED }
                if(jobState.groups.values.all { it == State.COMPLETED }) {
                    jobState.state = State.COMPLETED
                }
            }
        }
        om.writeValueAsString(jobState)
    }

    val groupsAggregator = Aggregator {k: String, v: String, g: String ->
        val groupState = om.readValue<GroupState>(g)
        val event = om.readValue<Event>(v)
        when(event.action) {
            Action.SENT_TO_PRICING -> {
                groupState.jobId = event.jobId // just in case its not set yet
                groupState.groupId = event.groupId // just in case its not set yet

                groupState.contracts.computeIfAbsent(event.contractId!!) { State.STARTED }
            }
            Action.FAILED_IN_PRICING -> {
                groupState.contracts.computeIfAbsent(event.contractId!!) { State.FAILED }
                groupState.state = State.FAILED
            }
            Action.SENT_TO_BILLING -> {
                groupState.contracts.computeIfAbsent(event.contractId!!) { State.COMPLETED_PRICING }
            }
            Action.FAILED_IN_BILLING -> {
                groupState.contracts.computeIfAbsent(event.contractId!!) { State.FAILED }
                groupState.state = State.FAILED
            }
            Action.COMPLETED -> {
                if(groupState.contracts.values.all { it == State.COMPLETED }) {
                    sendEvent_CompletedGroup(groupState.jobId, groupState.groupId)
                }
            }
            Action.COMPLETED_GROUP -> {
                groupState.contracts.computeIfAbsent(event.contractId!!) { State.COMPLETED }
                groupState.state = State.COMPLETED
            }
        }
        om.writeValueAsString(groupState)
    }

    val contractsAggregator = Aggregator { _: String, v: String, c: String ->
        val contractState = om.readValue<ContractState>(c)
        val event = om.readValue<Event>(v)
        when(event.action) {
            Action.SENT_TO_PRICING -> {
                contractState.jobId = event.jobId // just in case its not set yet
                contractState.groupId = event.groupId // just in case its not set yet
                contractState.contractId = event.contractId!! // just in case its not set yet

                contractState.contract = event.contract!!
            }
            Action.FAILED_IN_PRICING -> {
                contractState.state = State.FAILED
            }
            Action.SENT_TO_BILLING -> {
                contractState.state = State.COMPLETED_PRICING
            }
            Action.FAILED_IN_BILLING -> {
                contractState.state = State.FAILED
            }
            Action.COMPLETED -> {
                contractState.state = State.COMPLETED
            }
            Action.COMPLETED_GROUP -> Unit // noop
        }
        om.writeValueAsString(contractState)
    }

    @PreDestroy
    @SuppressWarnings("unused")
    fun predestroy() {
        streams.close()
    }

    @GET
    @Path("/jobs")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun getJobs(): Response {
        val jobs = mutableListOf<JobState>()
        jobsView.all().forEachRemaining { jobs.add(om.readValue(it.value)) }
        return Response.ok(jobs).build()
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

    fun getContract(jobId: UUID, contractId: UUID) = om.readValue<ContractState>(contractsView["$jobId::$contractId"])

    @GET
    @Path("/contract/{jobId}/{contractId}")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun getContractREST(@Parameter(name = "jobId") @PathParam("jobId") jobId: UUID, @Parameter(name = "contractId") @PathParam("contractId") contractId: UUID): Response {
        return Response.ok(contractsView["$jobId::$contractId"]).build()
    }

    private fun sendEvent_CompletedGroup(jobId: UUID, groupId: UUID) {
        eventService.sendEvent(Event(Action.COMPLETED_GROUP, jobId, groupId))
    }

}

data class JobState(var jobId: UUID,
                    var state: State,
                    var groups: HashMap<UUID, State>, // 1'000 x 36-chars-UUID + 10-chars-state => less than 100kb?

                    // estimates:

                    var numContractsTotal: Int,
                    var numContractsPricing: Int,
                    var numContractsPricingFailed: Int,
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
    STARTED, COMPLETED_PRICING, COMPLETED, FAILED
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
            Contract(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "", emptyList(), emptyList()))
}
