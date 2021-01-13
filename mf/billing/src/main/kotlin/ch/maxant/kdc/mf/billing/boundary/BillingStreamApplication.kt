package ch.maxant.kdc.mf.billing.boundary

import ch.maxant.kdc.mf.library.Context.Companion.EVENT
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.quarkus.runtime.StartupEvent
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
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.time.LocalDate
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

    @ConfigProperty(name = "kafka.bootstrap.servers")
    val kafkaBootstrapServers: String,

    @ConfigProperty(name = "ch.maxant.kdc.mf.billing.failRandomlyForTestingPurposes", defaultValue = "false")
    var failRandomlyForTestingPurposes: Boolean
) {
    private lateinit var jobsView: ReadOnlyKeyValueStore<String, String>

    private lateinit var groupsView: ReadOnlyKeyValueStore<String, String>

    private lateinit var streams: KafkaStreams

    private val random = Random()

    private val log = Logger.getLogger(this.javaClass)

    @SuppressWarnings("unused")
    fun init(@Observes e: StartupEvent) {



        val props = Properties()
        props[StreamsConfig.APPLICATION_ID_CONFIG] = "mf-billing-streamapplication"
        props[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaBootstrapServers
        props[StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG] = Serdes.String()::class.java
        props[StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG] = Serdes.String()::class.java

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
        val jobsStoreName = "billing-store-jobs"
        val jobsStore: Materialized<String, String, KeyValueStore<Bytes, ByteArray>> = Materialized.`as`(jobsStoreName)
        streamOfGroups.groupByKey()
                .aggregate(Initializer { om.writeValueAsString(JobState()) },
                            jobsAggregator,
                            Named.`as`("billing-internal-aggregate-state-jobs"), Materialized.with(Serdes.String(), Serdes.String()))
                .toStream(Named.`as`("billing-internal-stream-state-jobs"))
                .peek {k, v -> log.info("aggregated group into job $k: $v")}
                .to(BILLING_INTERNAL_STATE_JOBS)
        builder.globalTable(BILLING_INTERNAL_STATE_JOBS, jobsStore)


        // GROUPS - single truth of true state relating to the billing of groups of contracts
        val groupsStoreName = "billing-store-groups"
        val groupsStore: Materialized<String, String, KeyValueStore<Bytes, ByteArray>> = Materialized.`as`(groupsStoreName)
        val groupsTable = streamOfGroups.groupBy { _, value -> om.readTree(value).get("groupId").asText() }
                .aggregate(Initializer { om.writeValueAsString(GroupState()) }, groupsAggregator,
                        Named.`as`("billing-internal-aggregate-state-groups"), groupsStore)
        groupsTable.toStream(Named.`as`("billing-internal-stream-state-groups"))
                    .peek {k, v -> log.info("aggregated group $k: $v")}
                    .to(BILLING_INTERNAL_STATE_GROUPS)

        // now route the processed group to wherever it needs to go, depending on the next process step
        val branches = builder.stream<String, String>(BILLING_INTERNAL_STATE_GROUPS)
            .branch(Named.`as`("billing-internal-branch"),
                Predicate{ _, v -> om.readValue<Group>(v).nextProcessStep == BillingProcessStep.READ_PRICE},
                Predicate{ _, v -> om.readValue<Group>(v).nextProcessStep == BillingProcessStep.RECALCULATE_PRICE},
                Predicate{ _, v -> om.readValue<Group>(v).nextProcessStep == BillingProcessStep.BILL}
            )

        // READ_PRICE
        branches[0]
            .map<String, String>({ k, v ->
                KeyValue(k, buildReadPricesCommand(v))
             }, Named.`as`("billing-internal-to-read-pricing-mapper"))
            .transform(TransformerSupplier<String, String, KeyValue<String, String>>{
                MfTransformer("READ_PRICES_FOR_GROUP_OF_CONTRACTS")
            })
            .to("contracts-event-bus")

        // RECALCULATE_PRICE
        branches[1]
            .map<String, String>({ k, v ->
                TODO() //KeyValue(k, v) // TODO
            }, Named.`as`("billing-internal-to-recalculate-pricing-mapper"))
            .transform(TransformerSupplier<String, String, KeyValue<String, String>>{
                MfTransformer("RECALCULATE_PRICES_FOR_GROUP_OF_CONTRACTS")
            })
            .to("contracts-event-bus")

        // BILL
        branches[2]
            .peek {k, v -> log.info("sending group for internal billing $k: $v")}
            .to("billing-internal-bill")

        val topology = builder.build()
        println(topology.describe())

        streams = KafkaStreams(topology, props)

        streams.start()

        jobsView = streams.store(StoreQueryParameters.fromNameAndType(jobsStoreName, QueryableStoreTypes.keyValueStore<String, String>()))
        groupsView = streams.store(StoreQueryParameters.fromNameAndType(groupsStoreName, QueryableStoreTypes.keyValueStore<String, String>()))

        println(topology.describe())
    }

    private fun buildReadPricesCommand(v: String): String {
        val group = om.readValue<Group>(v)

        val fail = failRandomlyForTestingPurposes && random.nextInt(100) == 1
        if(fail) {
            log.warn("failing job ${group.jobId} and group ${group.groupId} for testing purposes at pricing!")
        }

        val commands = mutableListOf<PricingCommand>()
        for(contract in group.contracts) {
            for(period in contract.basePeriodsToPrice) {
                commands.add(PricingCommand(contract.contractId, period.from, period.to))
            }
        }
        val command = PricingCommandGroup(group.groupId, commands, false, fail)
        return om.writeValueAsString(command)
    }

    val jobsAggregator = Aggregator {k: String, v: String, j: String ->
        val jobState = om.readValue<JobState>(j)
        val group = om.readValue<Group>(v)
        when(group.failedProcessStep) {
            BillingProcessStep.READ_PRICE, BillingProcessStep.RECALCULATE_PRICE  -> {
                jobState.groups.computeIfAbsent(group.groupId) { State.FAILED }
                jobState.state = State.FAILED
                jobState.numContractsPricingFailed += group.contracts.size
            }
            BillingProcessStep.BILL  -> {
                jobState.groups.computeIfAbsent(group.groupId) { State.FAILED }
                jobState.state = State.FAILED
                jobState.numContractsBillingFailed += group.contracts.size
            }
            else -> Unit // noop
        }
        if(group.failedProcessStep != null) {
            when(group.nextProcessStep) {
                BillingProcessStep.READ_PRICE, BillingProcessStep.RECALCULATE_PRICE  -> {
                    jobState.jobId = group.jobId // just in case its not set yet
    
                    jobState.groups.computeIfAbsent(group.groupId) { State.STARTED }
                    jobState.numContractsTotal += group.contracts.size
                    jobState.numContractsPricing += group.contracts.size
                }
                BillingProcessStep.BILL -> {
                    jobState.numContractsBilling += group.contracts.size
                }
                BillingProcessStep.COMMS -> {
                    jobState.groups[group.groupId] = State.COMPLETED
                    if(jobState.groups.values.all { it == State.COMPLETED }) {
                        jobState.state = State.COMPLETED
                        TODO() // TODO send event to the world
                    }
                    jobState.numContractsComplete += group.contracts.size
                }
            }
        }
        om.writeValueAsString(jobState)
    }

    val groupsAggregator = Aggregator {k: String, v: String, g: String ->
        val groupState = om.readValue<GroupState>(g)
        val group = om.readValue<Group>(v)
        when(group.failedProcessStep) {
            BillingProcessStep.READ_PRICE, BillingProcessStep.RECALCULATE_PRICE  -> {
                groupState.state = State.FAILED
            }
            BillingProcessStep.BILL  -> {
                groupState.state = State.FAILED
            }
            else -> Unit // noop
        }
        if(group.failedProcessStep != null) {
            when(group.nextProcessStep) {
                BillingProcessStep.READ_PRICE, BillingProcessStep.RECALCULATE_PRICE  -> {
                    groupState.jobId = group.jobId // just in case its not set yet
                    groupState.groupId = group.groupId // just in case its not set yet
                }
                BillingProcessStep.BILL -> Unit // noop
                BillingProcessStep.COMMS -> {
                    groupState.state = State.COMPLETED
                }
            }
        }
        om.writeValueAsString(groupState)
    }

    @PreDestroy
    @SuppressWarnings("unused")
    fun predestroy() {
        streams.close()
    }

    @GET
    @Path("/jobs")
    @Operation(summary = "NOTE: knows about every job!")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun getJobs(): Response {
        val jobs = mutableListOf<JobState>()
        jobsView.all().forEachRemaining { jobs.add(om.readValue(it.value)) }
        return Response.ok(jobs).build()
    }

    @GET
    @Path("/job/{id}")
    @Operation(summary = "NOTE: knows about every job!")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun getJob(@Parameter(name = "id") @PathParam("id") id: UUID): Response {
        return Response.ok(jobsView[id.toString()]).build()
    }

    @GET
    @Path("/group/{id}")
    @Operation(summary = "NOTE: only knows about local state!")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun getGroup(@Parameter(name = "id") @PathParam("id") id: UUID): Response {
        return Response.ok(groupsView[id.toString()]).build()
    }

    /** NOTE: only knows about local state! */
    fun getContract(groupId: UUID, contractId: UUID) = om.readValue<GroupState>(groupsView["$groupId"]).contracts[contractId]!!

    companion object {
        const val BILLING_INTERNAL_STATE_JOBS = "billing-internal-state-jobs"
        const val BILLING_INTERNAL_STATE_GROUPS = "billing-internal-state-groups"
    }

}

class MfTransformer(private val event: String): Transformer<String, String, KeyValue<String, String>> {
    private lateinit var context: ProcessorContext

    override fun init(context: ProcessorContext) {
        this.context = context
    }

    override fun transform(key: String, value: String): KeyValue<String, String> {
        context.headers().add(EVENT, event.toByteArray(StandardCharsets.UTF_8))
        return KeyValue(key, value)
    }

    override fun close() {
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
    STARTED, COMPLETED, FAILED
}

// 1'000 x 1'000 bytes => less than 1MB? that's the aim, as kafkas default max record size is 1mb
// - control it using group size! 1'000 is probably too big as it is, regarding reading/writing to DB
data class GroupState(var jobId: UUID,
                      var groupId: UUID,
                      var state: State,
                      var contracts: HashMap<UUID, Contract>,
                      var started: LocalDateTime,
                      var finished: LocalDateTime?
) {
    constructor():
            this(UUID.randomUUID(), UUID.randomUUID(), State.STARTED, hashMapOf<UUID, Contract>(), LocalDateTime.now(), null)
}

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// internal models which is enriched as we go through the billing process
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
data class Group(val jobId: UUID,
                 val groupId: UUID,
                 val contracts: List<Contract>,
                 val nextProcessStep: BillingProcessStep,   // effectively, where to send the model next, because in
                                                            // order to ensure that the state that is written to rocksdb
                                                            // is always up to date before it is used, we need a
                                                            // "point-to-point" architecture in that we send data from
                                                            // one station to the next. the state store is one of those
                                                            // stations, and is in charge of updating the global ktable
                                                            // before sending a command to the next component to execute
                                                            // the next process step
                 val failedProcessStep: BillingProcessStep? = null, // where, if at all the group failed
                 val started: LocalDateTime = LocalDateTime.now()
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
