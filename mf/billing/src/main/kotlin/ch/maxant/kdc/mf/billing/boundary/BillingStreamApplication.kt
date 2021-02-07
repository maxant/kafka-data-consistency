package ch.maxant.kdc.mf.billing.boundary

import ch.maxant.kdc.mf.library.Context.Companion.COMMAND
import ch.maxant.kdc.mf.library.Context.Companion.EVENT
import ch.maxant.kdc.mf.library.Context.Companion.REQUEST_ID
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.quarkus.arc.Arc
import io.quarkus.runtime.StartupEvent
import io.smallrye.mutiny.Multi
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.*
import org.apache.kafka.streams.kstream.*
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
import java.io.PrintWriter
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.PreDestroy
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.Observes
import javax.inject.Inject
import javax.servlet.AsyncContext
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

    @ConfigProperty(name = "kafka.bootstrap.servers")
    val kafkaBootstrapServers: String,

    @ConfigProperty(name = "ch.maxant.kdc.mf.billing.failRandomlyForTestingPurposes", defaultValue = "true")
    var failRandomlyForTestingPurposes: Boolean
) {
    // TODO tidy the entries up when they are no longer in use! tip: see isCancelled below - altho theyre already removed with onterminate at the bottom?
    val subscriptions = ConcurrentHashMap<String, EmitterState>()

    private lateinit var jobsView: ReadOnlyKeyValueStore<String, String>

    private lateinit var groupsView: ReadOnlyKeyValueStore<String, String>

    private lateinit var globalGroupsView: ReadOnlyKeyValueStore<String, String>

    private lateinit var streams: KafkaStreams

    private val random = Random()

    private val log = Logger.getLogger(this.javaClass)

    val groupsStoreName = "billing-store-groups"

    val globalGroupsStoreName = "billing-store-groups-all"

    val allJobsStoreName = "billing-store-jobs-all"

    @SuppressWarnings("unused")
    fun init(@Observes e: StartupEvent) {
        Multi.createFrom()
            .ticks() // create a heartbeat
            .every(Duration.ofSeconds(1))
            .subscribe().with { sendToSubscribers("""{"type": "Heartbeat", "beat": $it}""") }

        val props = Properties()
        props[StreamsConfig.APPLICATION_ID_CONFIG] = "mf-billing-streamapplication"
        props[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaBootstrapServers
        props[StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG] = Serdes.String()::class.java
        props[StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG] = Serdes.String()::class.java
        // tune, so we dont wait for a long time:
        // we'd like to keep this higher but for individual billing,
        // we end up waiting 7 seconds in total if this is at 1000.
        // see: https://kafka.apache.org/documentation/streams/developer-guide/memory-mgmt.html
        // props[StreamsConfig.PROCESSING_GUARANTEE_CONFIG] = StreamsConfig.EXACTLY_ONCE // default is AT_LEAST_ONCE
        props[StreamsConfig.COMMIT_INTERVAL_MS_CONFIG] = 100 // default is 30_000 for at_least_once
        // props[StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG] = 1024 * 1024 // default is 10 mb

        // lets scale internally a little
        props[StreamsConfig.NUM_STREAM_THREADS_CONFIG] = 3 // default is 1

        val builder = StreamsBuilder()

        val streamOfGroups = builder.stream<String, String>("billing-internal-stream")

        // we do the following, so that we dont have to create locks across JVMs, for updating state. imagine two pods
        // processing two results and updating stats in the job at the same time after both have read the latest state
        // from the GKT/KT. the last one wins and overwrites the data written by the first one.
        // doing it this way with the aggregate, we are guaranteed to have correct data.
        // this is the kafka way of doing things.

        // JOBS - this state is NOT guaranteed to be totally up to date with every group, as it might be processed
        // after a group is processed and sent downstream, processed and returned! the single source of synchronous
        // truth is the group state!
        val jobsStateStream = streamOfGroups
                .groupBy { _, v -> om.readTree(v).get("jobId").asText() }
                .aggregate(Initializer { om.writeValueAsString(JobEntity()) },
                            jobsAggregator,
                            Named.`as`("billing-internal-aggregate-state-jobs"),
                            Materialized.`as`("billing-store-jobs"))
                .toStream(Named.`as`("billing-internal-stream-state-jobs"))

        // publish aggregated job state to a topic in order to build a GKT from it...
        jobsStateStream
                //.peek {k, _ -> log.info("aggregated group into job $k")}
                .to(BILLING_INTERNAL_STATE_JOBS)

        val allJobsStore: Materialized<String, String, KeyValueStore<Bytes, ByteArray>> = Materialized.`as`(allJobsStoreName)
        builder.globalTable(BILLING_INTERNAL_STATE_JOBS, allJobsStore)

        // ... and then get any completed jobs and send an event about that fact
        jobsStateStream
            .filter(
                { _, v -> catching(v) {
                    val js = om.readValue<JobEntity>(v)
                    js.completed != null && js.state == JobState.COMPLETED
                } },
                Named.`as`("billing-internal-branch-job-successful")
            )
            .transform(TransformerSupplier<String, String, KeyValue<String, String>>{
                MfTransformer(EVENT, "BILL_CREATED")
            })
            .transform(TransformerSupplier<String, String, KeyValue<String, String>>{
                MfTransformer(REQUEST_ID)
            })
            .peek {k, v -> log.info("publishing successful billing event $k: $v")}
            .to("billing-events")

        // GROUPS - single source of truth of true state relating to the billing of a groups of contracts
        val groupsStore: Materialized<String, String, KeyValueStore<Bytes, ByteArray>> = Materialized.`as`(groupsStoreName)
        val groupsTable = streamOfGroups
                .groupByKey()
                .aggregate(Initializer { om.writeValueAsString(GroupEntity()) },
                        groupsAggregator,
                        Named.`as`("billing-internal-aggregate-state-groups"),
                        groupsStore)
        val groupsStream = groupsTable.toStream(Named.`as`("billing-internal-stream-state-groups"))
        groupsStream.peek {k, v -> log.info("aggregated group $k with ${om.readTree(v).get("group").get("contracts").size()} contracts")}
                    .to(BILLING_INTERNAL_STATE_GROUPS)

        // global GROUPS - just so that we can fetch data for the UI!
        val globalGroupsStore: Materialized<String, String, KeyValueStore<Bytes, ByteArray>> = Materialized.`as`(globalGroupsStoreName)
        builder.globalTable(BILLING_INTERNAL_STATE_GROUPS, globalGroupsStore)

        // now route the processed group to wherever it needs to go, depending on the next process step
        val branches = groupsStream
            .branch(Named.`as`("billing-internal-branch"),
                Predicate{ _, v -> catching(v) { om.readValue<GroupEntity>(v).group.nextProcessStep == BillingProcessStep.READ_PRICE } },
                Predicate{ _, v -> catching(v) { om.readValue<GroupEntity>(v).group.nextProcessStep == BillingProcessStep.RECALCULATE_PRICE } },
                Predicate{ _, v -> catching(v) { om.readValue<GroupEntity>(v).group.nextProcessStep == BillingProcessStep.BILL } }
            )

        // READ_PRICE
        branches[0]
            .map<String, String>({ k, v ->
                KeyValue(k, buildReadPricesCommand(v))
             }, Named.`as`("billing-internal-to-read-pricing-mapper"))
            .transform(TransformerSupplier<String, String, KeyValue<String, String>>{
                MfTransformer(COMMAND, "READ_PRICES_FOR_GROUP_OF_CONTRACTS")
            })
            .peek {k, v -> log.info("sending group $k with ${om.readTree(v).get("commands").size()} contracts to pricing#read")}
            .to("contracts-event-bus")

        // RECALCULATE_PRICE
        branches[1]
            .map<String, String>({ _, _ ->
                TODO() //KeyValue(k, v) // TODO
            }, Named.`as`("billing-internal-to-recalculate-pricing-mapper"))
            .transform(TransformerSupplier<String, String, KeyValue<String, String>>{
                MfTransformer(COMMAND, "RECALCULATE_PRICES_FOR_GROUP_OF_CONTRACTS")
            })
            .peek {k, v -> log.info("sending group $k with ${om.readTree(v).get("commands").size()} contracts to pricing#recalculate")}
            .to("contracts-event-bus")

        // BILL
        branches[2]
            .map<String, String>({ k, v ->
                KeyValue(k, mapGroupEntityToGroup(v))
            }, Named.`as`("billing-internal-to-billing-mapper"))
            .transform(TransformerSupplier<String, String, KeyValue<String, String>>{
                MfTransformer(COMMAND, BILL_GROUP)
            })
            .peek {k, v -> log.info("sending group $k with ${om.readTree(v).get("contracts").size()} contracts for internal billing")}
            .to("billing-internal-bill")

        val topology = builder.build()
        println(topology.describe())

        streams = KafkaStreams(topology, props)

        streams.setUncaughtExceptionHandler { thread, throwable ->
            log.error("BSA002 handling an uncaught error on thread $thread: ${throwable.message} AND SHUTTING DOWN", throwable )
            Arc.shutdown()
        }

        streams.start()

        println(topology.describe())
    }

    private fun catching(value: String, f: () -> Boolean) =
        try {
            f()
        } catch(e: Exception) {
            log.error("BSA003 Failed to do something ${e.message} AND DISPOSING OF RECORD WITH VALUE $value AWAY!", e)
            false
        }

    private fun buildReadPricesCommand(v: String): String {
        val state = om.readValue<GroupEntity>(v)

        val fail = failRandomlyForTestingPurposes && random.nextInt(50) == 1
        if(fail) {
            log.warn("failing job ${state.group.jobId} and group ${state.group.groupId} for testing purposes at pricing!")
        }

        val commands = mutableListOf<PricingCommand>()
        for(contract in state.group.contracts) {
            for(period in contract.basePeriodsToPrice) {
                commands.add(PricingCommand(contract.contractId, period.from, period.to))
            }
        }
        val command = PricingCommandGroup(state.group.groupId, commands, false, fail)
        return om.writeValueAsString(command)
    }

    private fun mapGroupEntityToGroup(v: String): String {
        val state = om.readValue<GroupEntity>(v)
        return om.writeValueAsString(state.group)
    }

    val jobsAggregator = Aggregator {_: String, v: String, j: String ->
        val jobEntity = om.readValue<JobEntity>(j)
        val group = om.readValue<Group>(v)

        // remove groups that are being retried, so that in the end, the state will be successful, rather than a mix
        moveFailedGroupInJobEntity(group, jobEntity)

        jobEntity.jobId = group.jobId // just in case its not set yet
        jobEntity.state = JobState.STARTED
        jobEntity.numContractsByGroupId[group.groupId] = group.contracts.size
        if(!jobEntity.groups.containsKey(group.groupId)) {
            jobEntity.groups[group.groupId] = GroupState.STARTED
        }

        when(group.failedProcessStep) {
            BillingProcessStep.READ_PRICE, BillingProcessStep.RECALCULATE_PRICE  -> {
                jobEntity.groups[group.groupId] = GroupState.FAILED_PRICING
            }
            BillingProcessStep.BILL  -> {
                jobEntity.groups[group.groupId] = GroupState.FAILED_BILLING
            }
            else -> Unit // noop
        }
        when(group.nextProcessStep) {
            BillingProcessStep.READ_PRICE, BillingProcessStep.RECALCULATE_PRICE  -> {
                jobEntity.groups[group.groupId] = GroupState.PRICING
            }
            BillingProcessStep.BILL -> {
                jobEntity.groups[group.groupId] = GroupState.BILLING
            }
            BillingProcessStep.COMMS -> {
                jobEntity.groups[group.groupId] = GroupState.COMPLETED
            }
        }
        if(jobEntity.groups.values.all { it == GroupState.COMPLETED || it == GroupState.FAILED_PRICING || it == GroupState.FAILED_BILLING }) {
            if(jobEntity.groups.values.all { it == GroupState.COMPLETED }) {
                jobEntity.state = JobState.COMPLETED
            } else {
                jobEntity.state = JobState.FAILED
            }
            jobEntity.completed = LocalDateTime.now()
        } else {
            jobEntity.state = JobState.STARTED
            jobEntity.completed = null
        }

        jobEntity.numContractsTotal = jobEntity.numContractsByGroupId.values.sum()
        jobEntity.numContractsPricing = jobEntity.groups.filter { it.value == GroupState.PRICING || it.value == GroupState.BILLING || it.value == GroupState.COMPLETED }.map { jobEntity.numContractsByGroupId[it.key]?:0 }.sum()
        jobEntity.numContractsPricingFailed = jobEntity.groups.filter { it.value == GroupState.FAILED_PRICING }.map { jobEntity.numContractsByGroupId[it.key]?:0 }.sum()
        jobEntity.numContractsBilling = jobEntity.groups.filter { it.value == GroupState.BILLING || it.value == GroupState.COMPLETED }.map { jobEntity.numContractsByGroupId[it.key]?:0 }.sum()
        jobEntity.numContractsBillingFailed = jobEntity.groups.filter { it.value == GroupState.FAILED_BILLING }.map { jobEntity.numContractsByGroupId[it.key]?:0 }.sum()
        jobEntity.numContractsComplete = jobEntity.groups.filter { it.value == GroupState.COMPLETED }.map { jobEntity.numContractsByGroupId[it.key]?:0 }.sum()
        jobEntity.numRetriedGroups = jobEntity.retriedGroups.size

        om.writeValueAsString(jobEntity)
    }

    private fun moveFailedGroupInJobEntity(group: Group, jobEntity: JobEntity) {
        if (group.failedGroupId != null) {
            if (jobEntity.numContractsByGroupId.containsKey(group.failedGroupId)) {
                jobEntity.retriedNumContractsByGroupId[group.failedGroupId] = jobEntity.numContractsByGroupId[group.failedGroupId]!!
            }
            jobEntity.numContractsByGroupId.remove(group.failedGroupId)

            if (jobEntity.groups.containsKey(group.failedGroupId)) {
                jobEntity.retriedGroups[group.failedGroupId] = jobEntity.groups[group.failedGroupId]!!
            }
            jobEntity.groups.remove(group.failedGroupId)
        }
    }

    val groupsAggregator = Aggregator {k: String, v: String, g: String ->
        try {
            val groupEntity = om.readValue<GroupEntity>(g)
            val group = om.readValue<Group>(v)

            // copy latest state from group into state object
            groupEntity.group = group

            if(group.failedProcessStep != null) {
                groupEntity.state = when(group.failedProcessStep) {
                    BillingProcessStep.READ_PRICE, BillingProcessStep.RECALCULATE_PRICE -> GroupState.FAILED_PRICING
                    BillingProcessStep.BILL -> GroupState.FAILED_BILLING
                    BillingProcessStep.COMMS -> TODO()
                }
                groupEntity.finished = LocalDateTime.now()
                groupEntity.failedReason = group.failedReason
            }

            when(group.nextProcessStep) {
                BillingProcessStep.READ_PRICE, BillingProcessStep.RECALCULATE_PRICE -> groupEntity.state = GroupState.PRICING
                BillingProcessStep.BILL -> groupEntity.state = GroupState.BILLING
                BillingProcessStep.COMMS -> {
                    groupEntity.state = GroupState.COMPLETED
                    groupEntity.finished = LocalDateTime.now()
                }
            }

            om.writeValueAsString(groupEntity)
        } catch(e: Exception) {
            log.error("BSA001 Failed to aggregate group ${e.message} - DISPOSING OF RECORD WITH KEY $k AND VALUE $v", e)
            g
        }
    }

    @PreDestroy
    @SuppressWarnings("unused")
    fun predestroy() {
        streams.close()
    }

    fun sendToSubscribers(data: String) {
        val toRemove = mutableSetOf<String>()
        subscriptions
            .entries
            .forEach {
                synchronized(it.value) { // TODO is this necessary? does it hurt??
                    if(it.value.isExpired()) {
                        handleExpiredSubscriber(it, toRemove)
                    } else {
                        sendToSubscriber(it, data, toRemove)
                    }
                }
            }
        toRemove.forEach { subscriptions.remove(it) }
    }

    private fun sendToSubscriber(
        subscriber: MutableMap.MutableEntry<String, EmitterState>,
        data: String,
        toRemove: MutableSet<String>
    ): Any {
        subscriber.value.touch()
        if (!data.contains("Heartbeat")) {
            log.info("emitting data to subscriber ${subscriber.key}")
        }
        return try {
            subscriber.value.writer.write("data: $data\n\n")
            // https://stackoverflow.com/questions/10878243/sse-and-servlet-3-0
            if (subscriber.value.writer.checkError()) { //checkError calls flush, and flush() does not throw IOException
                toRemove.add(subscriber.key)
            }
            Unit
        } catch (e: Exception) {
            toRemove.add(subscriber.key)
        }
    }

    private fun handleExpiredSubscriber(subscriber: MutableMap.MutableEntry<String, EmitterState>, toRemove: MutableSet<String>
    ) {
        log.info("closing expired sse connection. expired: ${subscriber.value.isExpired()}")
        toRemove.add(subscriber.key)
        try {
            subscriber.value.async.complete()
        } catch (e: Exception) {
            // TODO could be normal if the client is gone?
            log.warn("failed to close expired sse for ${subscriber.key}", e)
        }
    }

    /** seems to not like immediate fetching of the store during init - so lets do it lazily */
    private fun getAllJobs() =
        try {
            jobsView.all()
        } catch(e: UninitializedPropertyAccessException) {
            jobsView = streams.store(StoreQueryParameters.fromNameAndType(allJobsStoreName, QueryableStoreTypes.keyValueStore<String, String>()))
            jobsView.all()
        }

    /** seems to not like immediate fetching of the store during init - so lets do it lazily */
    private fun getJob(key: String) =
        try {
            jobsView.get(key)
        } catch(e: UninitializedPropertyAccessException) {
            jobsView = streams.store(StoreQueryParameters.fromNameAndType(allJobsStoreName, QueryableStoreTypes.keyValueStore<String, String>()))
            jobsView.get(key)
        }

    /** seems to not like immediate fetching of the store during init - so lets do it lazily */
    fun getGlobalGroup(key: String) =
        try {
            globalGroupsView.get(key)
        } catch(e: UninitializedPropertyAccessException) {
            globalGroupsView = streams.store(StoreQueryParameters.fromNameAndType(globalGroupsStoreName, QueryableStoreTypes.keyValueStore<String, String>()))
            globalGroupsView.get(key)
        }

    /** seems to not like immediate fetching of the store during init - so lets do it lazily */
    private fun getGroup(key: String) =
        try {
            groupsView.get(key)
        } catch(e: UninitializedPropertyAccessException) {
            groupsView = streams.store(StoreQueryParameters.fromNameAndType(groupsStoreName, QueryableStoreTypes.keyValueStore<String, String>()))
            groupsView.get(key)
        }

    @GET
    @Path("/jobs")
    @Operation(summary = "NOTE: knows about every job!")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun getJobs(): Response {
        val jobs = mutableListOf<JobEntity>()
        getAllJobs().forEachRemaining { jobs.add(om.readValue(it.value)) }
        return Response.ok(jobs).build()
    }

    @GET
    @Path("/job/{id}")
    @Operation(summary = "NOTE: knows about every job!")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun getJob(@Parameter(name = "id") @PathParam("id") id: UUID): Response {
        return Response.ok(getJob(id.toString())).build()
    }

    @GET
    @Path("/group/{id}")
    @Operation(summary = "knows global state")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun getGlobalGroup(@Parameter(name = "id") @PathParam("id") id: UUID): Response {
        return Response.ok(getGlobalGroup(id.toString())).build()
    }

    /** NOTE: only knows about local state! */
    fun getGroup(groupId: UUID) = om.readValue<GroupEntity>(getGroup(groupId.toString())).group

    companion object {
        const val BILL_GROUP = "BILL_GROUP"
        const val BILLING_INTERNAL_STATE_JOBS = "billing-internal-state-jobs"
        const val BILLING_INTERNAL_STATE_GROUPS = "billing-internal-state-groups"
    }

}

class MfTransformer(private val headerName: String, private var headerValue: String = ""): Transformer<String, String, KeyValue<String, String>> {
    private lateinit var context: ProcessorContext

    override fun init(context: ProcessorContext) {
        this.context = context
    }

    override fun transform(key: String, value: String): KeyValue<String, String> {
        // use the key (jobId) as the requestId since the requestId was used as the jobId at the start of
        // a single contract job, so that the waiting UI can react
        if(headerName == REQUEST_ID) headerValue = key

        context.headers().add(headerName, headerValue.toByteArray(StandardCharsets.UTF_8))
        return KeyValue(key, value)
    }

    override fun close() {
    }
}

data class JobEntity(var jobId: UUID,
                    var state: JobState,
                    var groups: HashMap<UUID, GroupState>, // 1'000 x 36-chars-UUID + 10-chars-state => less than 100kb?
                    var numContractsByGroupId: HashMap<UUID, Int> = hashMapOf(), // 1'000 x 4 bytes => less than 4kb

                    // we move groups from main state to here, when they are retried, so that in the end, the job can become successful
                    var retriedGroups: HashMap<UUID, GroupState>, // 1'000 x 36-chars-UUID + 10-chars-state => less than 100kb?
                    var retriedNumContractsByGroupId: HashMap<UUID, Int> = hashMapOf(), // 1'000 x 4 bytes => less than 4kb

                    var numContractsTotal: Int,
                    var numContractsPricing: Int,
                    var numContractsPricingFailed: Int,
                    var numContractsBilling: Int,
                    var numContractsBillingFailed: Int,
                    var numContractsComplete: Int,
                    var numRetriedGroups: Int,

                    var started: LocalDateTime,
                    var completed: LocalDateTime?,
                    var type: String = "Job"
) {
    constructor():
            this(UUID.randomUUID(), JobState.STARTED, hashMapOf<UUID, GroupState>(), hashMapOf<UUID, Int>(),
                hashMapOf<UUID, GroupState>(), hashMapOf<UUID, Int>(), 0, 0, 0, 0, 0, 0, 0, LocalDateTime.now(), null)
}

enum class JobState {
    STARTED,
    COMPLETED,
    FAILED
}

enum class GroupState {
    STARTED,
    PRICING,
    FAILED_PRICING,
    BILLING,
    FAILED_BILLING,
    COMPLETED
}

// 1'000 x 1'000 bytes => less than 1MB? that's the aim, as kafkas default max record size is 1mb
// - control it using group size! 1'000 is probably too big as it is, regarding reading/writing to DB
data class GroupEntity(var group: Group,
                      var state: GroupState,
                      var started: LocalDateTime,
                      var finished: LocalDateTime?,
                      var failedReason: String? = null,
                      var type: String = "Group"
) {
    constructor():
            this(Group(UUID.randomUUID(), UUID.randomUUID(), listOf(), BillingProcessStep.READ_PRICE), GroupState.STARTED, LocalDateTime.now(), null)
}

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// internal models which is enriched as we go through the billing process
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
data class Group(val jobId: UUID,
                 val groupId: UUID,
                 val contracts: List<Contract>,
                 val nextProcessStep: BillingProcessStep?,  // effectively, where to send the model next, because in
                                                            // order to ensure that the state that is written to rocksdb
                                                            // is always up to date before it is used, we need a
                                                            // "point-to-point" architecture in that we send data from
                                                            // one station to the next. the state store is one of those
                                                            // stations, and is in charge of updating the global ktable
                                                            // before sending a command to the next component to execute
                                                            // the next process step. null if failed.
                 val failedProcessStep: BillingProcessStep? = null, // where, if at all the group failed
                 val started: LocalDateTime = LocalDateTime.now(),
                 val failedReason: String? = null,
                 val failedGroupId: UUID? = null // the group that this one is replacing
)

data class Contract(val jobId: UUID,
                    val groupId: UUID,
                    val contractId: UUID,
                    val billingDefinitionId: String,
                    val basePeriodsToPrice: List<Period>,
                    val periodsToBill: List<BillPeriod>
)

open class Period(val from: LocalDate,
                  val to: LocalDate,
                  var price: BigDecimal = BigDecimal.ZERO // initially unknown, but read/recalculated and written here
)

class BillPeriod(from: LocalDate,
                  to: LocalDate,
                  price: BigDecimal = BigDecimal.ZERO,
                  var billId: UUID? = null): Period(from, to, price)

enum class BillingProcessStep {
    READ_PRICE, // for new contracts, the price is already known, as it was offered to the customer, so just go read it
    RECALCULATE_PRICE, // for recurring billing, we first need to go recalculate the price
    BILL, // create the actual bill
    COMMS // send the bill to the customer
    // RECEIVE_PAYMENT, SEND_REMINDER, TERMINATE_CONTRACT, etc., etc.
}

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// pricing command => sent to pricing. dicated by the pricing component!
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
data class PricingCommandGroup(val groupId: UUID, val commands: List<PricingCommand>, val recalculate: Boolean, val failForTestingPurposes: Boolean)

data class PricingCommand(val contractId: UUID, val from: LocalDate, val to: LocalDate)

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// SSE model
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
data class EmitterState(val async: AsyncContext, val writer: PrintWriter, var lastUsed: Long = System.currentTimeMillis()) {
    private val fiveMins = 5 * 60 * 1_000

    fun isExpired() = System.currentTimeMillis() - this.lastUsed > fiveMins

    fun touch() {
        lastUsed = System.currentTimeMillis()
    }
}