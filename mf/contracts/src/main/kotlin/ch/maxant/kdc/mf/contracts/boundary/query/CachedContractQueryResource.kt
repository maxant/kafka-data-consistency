package ch.maxant.kdc.mf.contracts.boundary.query

import ch.maxant.kdc.mf.contracts.adapter.DiscountsSurchargesAdapter
import ch.maxant.kdc.mf.contracts.definitions.*
import ch.maxant.kdc.mf.contracts.entity.ComponentEntity
import ch.maxant.kdc.mf.contracts.entity.ContractEntity
import ch.maxant.kdc.mf.contracts.entity.ContractState
import ch.maxant.kdc.mf.library.KafkaHandler
import ch.maxant.kdc.mf.library.PimpedAndWithDltAndAck
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.smallrye.graphql.api.Context
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.eclipse.microprofile.graphql.*
import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Gauge
import org.eclipse.microprofile.metrics.annotation.Timed
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.elasticsearch.client.Request
import org.elasticsearch.client.ResponseException
import org.jboss.logging.Logger
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.net.ConnectException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.context.RequestScoped
import javax.inject.Inject
import javax.persistence.EntityManager
import javax.ws.rs.NotFoundException
import javax.ws.rs.WebApplicationException

private const val INDEX = "contract-cache"


/**
 * <pre>
query {
    cached_aggregate(id: "77c1917d-2061-42e6-9631-78f10cbae161") {

        contract {
            id
            createdAt
            contractState
        }

        components(definitionIdFilter:"C.*") {
        id
        parentId
        productId
        configs {key, value}
        componentDefinitionId
    }

        discountsAndSurcharges {
            failedReason
            discountsSurcharges {
                componentId addedManually definitionId value
            }
        }

        anythingMissing
    }
}
* </pre>
* see https://download.eclipse.org/microprofile/microprofile-graphql-1.0.3/microprofile-graphql.html#graphql_and_rest
* http://localhost:8080/graphql/schema.graphql
* http://localhost:8080/graphql-ui
*/
@GraphQLApi
@SuppressWarnings("unused")
class CachedContractQueryResource(
    @Inject var em: EntityManager,
    @Inject var om: ObjectMapper,
    @Inject var context: Context,
    @Inject var stats: GraphQlCacheStats,
    @Inject var cacheEsAdapter: CacheEsAdapter,
    @Inject var queryState: QueryState
) {
    @Inject
    @RestClient // bizarrely this doesnt work with constructor injection
    lateinit var discountsSurchargesAdapter: DiscountsSurchargesAdapter

    private val log = Logger.getLogger(this.javaClass)

    @Query("cached_aggregate")
    @Description("Get a contract by it's ID, referring to the cache first and lazy loading if required")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun findContractById(@Name("id") id: UUID, @Name("forceReload") @DefaultValue("false") forceReload: Boolean): CachedAggregate? {
        log.info("getting contract $id with context.arguments ${context.arguments.map { "${it.key}->${it.value}" }}")

        val start = System.currentTimeMillis()

        if(forceReload) {
            cacheEsAdapter.deleteFromCache(id)
        }

        var cached = try {
            cacheEsAdapter.readFromCache(id)
        } catch(e: Exception) {
            log.warn("failed to read from cache", e)
            null
        }
        if(cached == null) {
            log.info("cache miss")
            cached = assembleAggregate(id)
            try {
                cacheEsAdapter.writeToCache(cached)
            } catch(e: Exception) {
                stats.incrementCacheWriteErrors()
                log.warn("failed to write to cache", e)
            }
            stats.incrementCacheMisses()
        } else {
            cached.cacheHit = true
            stats.incrementCacheHits()

            if(cached.isAnythingMissing()) {
                log.info("hit but ameliorating")
                ameliorateAggregate(cached)
                cacheEsAdapter.writeToCache(cached)
            } else log.info("cache hit")
        }
        cached.loadedInMs = System.currentTimeMillis() - start
        log.info("done in ${cached.loadedInMs}ms")

        queryState.aggregate = cached // fill for use in calls by qraphql to subsequent resolvers

        return cached
    }

    private fun ameliorateAggregate(aggregate: CachedAggregate) {
        if(aggregate.discountsAndSurcharges.failedReason != null) {
            log.info("attempting to patch failed discounts/surcharges to cached aggregate ${aggregate.contract.id}")
            aggregate.discountsAndSurcharges = assembleDiscountsAndSurcharges(aggregate.contract.id)
        }
        if(aggregate.conditions.failedReason != null) {
            log.info("attempting to patch failed conditions to cached aggregate ${aggregate.contract.id}")
            aggregate.conditions = assembleConditions(aggregate.contract.id)
        }
    }

    private fun assembleAggregate(id: UUID): CachedAggregate {
        val contract = em.find(ContractEntity::class.java, id) ?: throw NotFoundException("No contract with id $id found")

        // val components = ComponentEntity.Queries.selectByContractId(em, contract.id)
        val components = em.createQuery("select c from ComponentEntity c where c.contractId = :contractId")
            .setParameter("contractId", contract.id)
            .resultList as List<ComponentEntity> // avoid named query, and cast because of this: https://quarkusio.zulipchat.com/#narrow/stream/187030-users/topic/Hibernate.2FGraphQL.20SRGQL012000.3A.20Data.20Fetching.20Error

        val discountsSurcharges = assembleDiscountsAndSurcharges(id)

        val conditions = assembleConditions(id)

        return CachedAggregate(Contract(contract), discountsSurcharges, conditions, components)
    }

    private fun assembleDiscountsAndSurcharges(id: UUID): DiscountsAndSurcharges =
        try {
            DiscountsAndSurcharges(discountsSurchargesAdapter.getByContractIdAsDto(id))
        } catch (e: Exception) {
            log.warn("failed to fetch discounts/surcharges", e)
            DiscountsAndSurcharges(emptyList(), e.message)
        }

    private fun assembleConditions(id: UUID): Conditions =
        try {
            throw RuntimeException("not implemented yet")
            //Conditions(discountsSurchargesAdapter.getByContractIdAsDto(id))
        } catch (e: Exception) {
            log.warn("failed to fetch conditions", e)
            Conditions(emptyList(), e.message)
        }

    fun components(@Source contract: Contract, // not used coz we use request scoped bean
                             @Name("definitionIdFilter") @Description("Filters components with definition IDs matching the given regex") @DefaultValue(".*") definitionIdFilter: String,
                             @Name("configNameFilter") @Description("Filters components with configs matching the given regex") @DefaultValue(".*") configNameFilter: String
    ) = components(definitionIdFilter, configNameFilter)

    fun components(@Source aggregate: CachedAggregate, // not used coz we use request scoped bean
                              @Name("definitionIdFilter") @Description("Filters components with definition IDs matching the given regex") @DefaultValue(".*") definitionIdFilter: String,
                              @Name("configNameFilter") @Description("Filters components with configs matching the given regex") @DefaultValue(".*") configNameFilter: String
    ) = components(definitionIdFilter, configNameFilter)

    private fun components(definitionIdFilter: String, configNameFilter: String): List<Component> {
        val definitionIdRegex = if(definitionIdFilter == ".*") null else Regex(definitionIdFilter)
        val configNameRegex = if(configNameFilter == ".*") null else Regex(configNameFilter)
        return queryState.aggregate._components
            .filter { definitionIdRegex?.matches(it.componentDefinitionId) ?: true }
            .filter { configNameRegex?.matches(it.configuration) ?: true }
            .map { Component(om, it) }
            .sortedBy { it.componentDefinitionId }
    }

    @Query("createdAt")
    fun createdAtFormattedByClient(@Name("pattern") @DefaultValue("yyyy-MM-dd") pattern: String, @Source contract: Contract): String =
        contract.createdAt.format(DateTimeFormatter.ofPattern(pattern))

    @Query("configs")
    fun configsSortedBy(@Source component: Component,
                        @Name("nameFilter") @DefaultValue(".*") nameFilter: String,
                        @Name("sortedBy") @DefaultValue("NAME") sortedBy: ConfigSortedBy
    ): List<Config> {
        val regex = if(nameFilter == ".*") null else Regex(nameFilter)

        return component.configs
            .filter { regex?.matches(it.name.toString()) ?: true }
            .sortedBy {
                when(sortedBy) {
                    ConfigSortedBy.NAME -> it.name.toString()
                    ConfigSortedBy.VALUE -> it.value
                }
            }
    }

    fun discountsAndSurcharges(@Source component: Component): List<DiscountSurcharge> {
        return queryState.aggregate.discountsAndSurcharges.list.filter { it.componentId == component.id }
    }

    fun conditions(@Source component: Component): List<Condition> {
        return queryState.aggregate.conditions.list.filter { it.componentId == component.id }
    }
}

@ApplicationScoped
class GraphQlCacheStats {

    val numHits = AtomicLong()
    val numMisses = AtomicLong()
    val numWriteErrors = AtomicLong()

    @Gauge(name = "numCacheHits", unit = MetricUnits.NONE, description = "Number of cache hits so far.")
    fun getCacheHits(): Long {
        return numHits.get()
    }

    @Gauge(name = "numCacheMisses", unit = MetricUnits.NONE, description = "Number of cache misses so far.")
    fun getCacheMisses(): Long {
        return numMisses.get()
    }

    @Gauge(name = "cacheHitRatio", unit = MetricUnits.NONE, description = "ratio of cache hits")
    fun getCacheHitRatio(): Double {
        val misses = numMisses.get()
        return numHits.get().toDouble() / (if(misses == 0L) 1.0 else misses.toDouble())
    }

    @Gauge(name = "numWriteErrors", unit = MetricUnits.NONE, description = "how often we failed to write to the cache")
    fun getNumWriteErrors(): Long {
        return numWriteErrors.get()
    }

    fun incrementCacheHits() {
        numHits.incrementAndGet()
    }

    fun incrementCacheMisses() {
        numMisses.incrementAndGet()
    }

    fun incrementCacheWriteErrors() {
        numWriteErrors.incrementAndGet()
    }
}

data class CachedAggregate(
    // funnily enough, as soon as we use a class in a query method, eg for filtering the components, it wants a
    // var instead of a val!
    // the error is: InvalidSchemaException: invalid schema: "ContractInput" must define one or more fields
    // => use vars everywhere, we also get the error in other cases, not really sure when/why. doesnt seem to support immutability
    var contract: Contract,

    var discountsAndSurcharges: DiscountsAndSurcharges, // var so it can be improved, if it failed
    var conditions: Conditions, // var so it can be improved, if it failed

    @Ignore
    var _components: List<ComponentEntity>
) {
    var cacheHit: Boolean = false
    var loadedInMs = 0L

    fun isAnythingMissing(): Boolean {
        if(discountsAndSurcharges.failedReason != null) {
            return true
        }
        if(conditions.failedReason != null) {
            return false // TODO once we impl it, use "true" - using false now to avoid having to keep updating the cache
        }
        return false
    }

    constructor(): this(Contract(), DiscountsAndSurcharges(emptyList()), Conditions(emptyList()), emptyList())  // required by smallrye graphql
}

data class Contract(
    var id: UUID,
    var start: LocalDateTime,
    var end: LocalDateTime,
    var contractState: ContractState,
    var syncTimestamp: Long,
    var createdAt: LocalDateTime,
    var createdBy: String,
    var offeredAt: LocalDateTime?,
    var offeredBy: String?,
    var acceptedAt: LocalDateTime?,
    var acceptedBy: String?,
    var approvedAt: LocalDateTime?,
    var approvedBy: String?,
    var profileId: ProfileId
) {
    constructor() : this(UUID.randomUUID(), LocalDateTime.MIN, LocalDateTime.MAX, ContractState.DRAFT, 0L,
        LocalDateTime.MIN, "", null, null, null, null, null, null, ProfileId.STANDARD) // required by smallrye graphql

    constructor(entity: ContractEntity) : this(
        entity.id,
        entity.start,
        entity.end,
        entity.contractState,
        entity.syncTimestamp,
        entity.createdAt,
        entity.createdBy,
        entity.offeredAt,
        entity.offeredBy,
        entity.acceptedAt,
        entity.acceptedBy,
        entity.approvedAt,
        entity.approvedBy,
        entity.profileId
    )
}

data class Component(
    var id: UUID,
    var parentId: UUID?,
    var componentDefinitionId: String,
    var configs: List<Config>,
    var productId: ProductId?
) {
    constructor() : this(UUID.randomUUID(), null, "", emptyList(), null) // required by smallrye graphql

    constructor(om: ObjectMapper, entity: ComponentEntity) : this(
        entity.id,
        entity.parentId,
        entity.componentDefinitionId,
        om.readValue<ArrayList<Configuration<*>>>(entity.configuration).map { Config(it.name, it.value.toString(), it.units) },
        entity.productId
    )
}

data class Config(var name: ConfigurableParameter, var value: String, var units: Units) {
    constructor(): this(ConfigurableParameter.VOLUME, "", Units.NONE) // required by smallrye graphql
}

data class DiscountsAndSurcharges(var list: List<DiscountSurcharge>, var failedReason: String? = null) {
    constructor(): this(emptyList()) // required by smallrye-graphql
}

data class DiscountSurcharge(
    var id: UUID,
    var componentId: UUID,
    var definitionId: String,
    var value: BigDecimal,
    var syncTimestamp: Long,
    var addedManually: Boolean
) {
    constructor(): this(UUID.randomUUID(), UUID.randomUUID(), "", BigDecimal.ZERO, 0L, false) // required by smallrye graphql
}

data class Conditions(var list: List<Condition>, var failedReason: String? = null) {
    constructor(): this(emptyList()) // required by smallrye-graphql
}

data class Condition(
    var id: UUID,
    var componentId: UUID,
    var definitionId: String,
    var syncTimestamp: Long,
    var addedManually: Boolean
) {
    constructor(): this(UUID.randomUUID(), UUID.randomUUID(), "", 0L, false) // required by smallrye graphql
}

enum class ConfigSortedBy { NAME, VALUE }

@ApplicationScoped
class CacheEsAdapter(
    @Inject var om: ObjectMapper,
    @Inject var context: Context,
    @Inject var restClient: org.elasticsearch.client.RestClient
) {
    private val log = Logger.getLogger(this.javaClass)

    @Timed(unit = MetricUnits.MILLISECONDS)
    fun writeToCache(aggregate: CachedAggregate) {
        log.info("writing aggregate to cache ${aggregate.contract.id}")
        val request = Request("PUT", "/$INDEX/_doc/${aggregate.contract.id}")
        request.setJsonEntity(om.writeValueAsString(aggregate))
        performESRequest(request)
    }

    @Timed(unit = MetricUnits.MILLISECONDS)
    fun readFromCache(id: UUID): CachedAggregate? {
        val request = Request("GET", "/$INDEX/_doc/${id}")
        val response = performESRequest(request) ?: return null
        val source = om.readTree(response).get("_source")
        return om.treeToValue(source, CachedAggregate::class.java)
    }

    @Timed(unit = MetricUnits.MILLISECONDS)
    fun deleteFromCache(id: UUID): String? {
        log.info("deleting from ES $id")
        val request = Request("DELETE", "/$INDEX/_doc/${id}")
        return performESRequest(request)
    }

    @Timed(unit = MetricUnits.MILLISECONDS)
    fun performESRequest(request: Request): String? {
        try {
            val response = restClient.performRequest(request)
            val statusCode = response.statusLine.statusCode
            if(statusCode < 200 || statusCode >= 300) {
                throw WebApplicationException("failed to ${request.method} contract with ES. ($statusCode) ${response.entity}")
            }
            val baos = ByteArrayOutputStream(response.entity.contentLength.toInt())
            response.entity.writeTo(baos)
            return String(baos.toByteArray())
        } catch(e: ConnectException) {
            throw WebApplicationException("failed to use ES. no connection", e)
        } catch(e: ResponseException) {
            if(e.response.statusLine.statusCode == 404) {
                return null
            }
            throw e
        } catch(e: Exception) {
            if(e is WebApplicationException){
                throw e
            }
            throw WebApplicationException("failed to use ES", e)
        }
    }

}

@ApplicationScoped
@SuppressWarnings("unused")
class CacheEvicter(
    @Inject var context: ch.maxant.kdc.mf.library.Context,
    @Inject var restClient: org.elasticsearch.client.RestClient
) : KafkaHandler {

    private val log = Logger.getLogger(this.javaClass)

    override fun getKey() = "event-bus-in"

    override fun getRunInParallel() = true

    @PimpedAndWithDltAndAck
    @Timed(unit = MetricUnits.MILLISECONDS)
    override fun handle(record: ConsumerRecord<String, String>) {
        when (context.event) {
            "UPDATED_DRAFT",
            "ADDED_DSC_FOR_DRAFT",
            "OFFERED_DRAFT",
            "ACCEPTED_OFFER",
            "APPROVED_CONTRACT" -> evict(record)
        }
    }

    private fun evict(record: ConsumerRecord<String, String>) {
        log.info("evicting ${record.key()}")

        val request = Request("DELETE", "/$INDEX/_doc/${record.key()}")
        try {
            restClient.performRequest(request)
        } catch(e: ResponseException) {
            if(e.response.statusLine.statusCode != 404) throw e // 404 is OK, eg no user ever searched for it in the cache
        } catch(e: Exception) {
            log.error("failed to evict ${record.key()}", e)
        }
    }
}

@RequestScoped
class QueryState {
    lateinit var aggregate: CachedAggregate
}