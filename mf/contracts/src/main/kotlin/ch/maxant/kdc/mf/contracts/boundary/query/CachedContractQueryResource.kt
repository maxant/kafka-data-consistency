package ch.maxant.kdc.mf.contracts.boundary.query

import ch.maxant.kdc.mf.contracts.adapter.DiscountsSurchargesAdapter
import ch.maxant.kdc.mf.contracts.definitions.ConfigurableParameter
import ch.maxant.kdc.mf.contracts.definitions.Configuration
import ch.maxant.kdc.mf.contracts.definitions.ProductId
import ch.maxant.kdc.mf.contracts.entity.ComponentEntity
import ch.maxant.kdc.mf.contracts.entity.ContractEntity
import ch.maxant.kdc.mf.contracts.entity.ContractState
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.smallrye.graphql.api.Context
import org.eclipse.microprofile.graphql.*
import org.eclipse.microprofile.graphql.DefaultValue
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
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.persistence.EntityManager
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

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
            failed
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
class CachedContractQueryResource(
    @Inject var em: EntityManager,
    @Inject var om: ObjectMapper,
    @Inject var context: Context,
    @Inject var restClient: org.elasticsearch.client.RestClient,
    @Inject var stats: GraphQlCacheStats
) {
    @Inject
    @RestClient // bizarrely this doesnt work with constructor injection
    lateinit var discountsSurchargesAdapter: DiscountsSurchargesAdapter

    private val INDEX = "contract-cache"

    private val log = Logger.getLogger(this.javaClass)

    @Query("cached_aggregate")
    @Description("Get a contract by it's ID, referring to the cache first and lazy loading if required")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun findContractById(@Name("id") id: UUID, @Name("forceReload") @DefaultValue("false") forceReload: Boolean): CachedAggregate {
        log.info("getting contract $id with context.arguments ${context.arguments.map { "${it.key}->${it.value}" }}")

        val start = System.currentTimeMillis()

        if(forceReload) {
            deleteFromCache(id)
        }

        var cached = try {
            readFromCache(id)
        } catch(e: Exception) {
            log.warn("failed to read from cache", e)
            null
        }
        if(cached == null) {
            log.info("cache miss")
            cached = assembleAggregate(id)
            try {
                writeToCache(cached)
            } catch(e: Exception) {
                stats.incrementCacheWriteErrors()
                log.warn("failed to write to cache", e)
            }
            stats.incrementCacheMisses()
        } else {
            stats.incrementCacheHits()

            if(cached.isAnythingMissing()) {
                log.info("hit but ameliorating")
                ameliorateAggregate(cached)
                writeToCache(cached)
            } else log.info("cache hit")
        }
        log.info("done in ${System.currentTimeMillis() - start}ms")
        return cached
    }

    private fun ameliorateAggregate(aggregate: CachedAggregate) {
        if(aggregate.discountsAndSurcharges.failed) {
            log.info("attempting to patch failed discounts/surcharges to cached aggregate ${aggregate.contract.id}")
            aggregate.discountsAndSurcharges = assembleDiscountsAndSurcharges(aggregate.contract.id)
        }
    }

    private fun assembleAggregate(id: UUID): CachedAggregate {
        val contract = em.find(ContractEntity::class.java, id)

        // val components = ComponentEntity.Queries.selectByContractId(em, contract.id)
        val components = em.createQuery("select c from ComponentEntity c where c.contractId = :contractId")
            .setParameter("contractId", contract.id)
            .resultList as List<ComponentEntity> // avoid named query, and cast because of this: https://quarkusio.zulipchat.com/#narrow/stream/187030-users/topic/Hibernate.2FGraphQL.20SRGQL012000.3A.20Data.20Fetching.20Error

        val discountsSurcharges = assembleDiscountsAndSurcharges(id)

        return CachedAggregate(Contract(contract), components, discountsSurcharges)
    }

    private fun assembleDiscountsAndSurcharges(id: UUID): DiscountsAndSurcharges =
        try {
            DiscountsAndSurcharges(discountsSurchargesAdapter.getByContractIdAsDto(id))
        } catch (e: Exception) {
            log.warn("failed to fetch discounts/surcharges", e)
            DiscountsAndSurcharges(emptyList(), true)
        }

    @Query("components")
    fun components(@Source aggregate: CachedAggregate, @Name("definitionIdFilter") definitionIdFilter: String): List<Component> {
        return aggregate._components
            .filter { Regex(definitionIdFilter).matches((it).componentDefinitionId) }
            .map { Component(om, it) }
    }

    private fun writeToCache(aggregate: CachedAggregate) {
        log.info("writing aggregate to cache ${aggregate.contract.id}")
        val request = Request("PUT", "/$INDEX/_doc/${aggregate.contract.id}")
        request.setJsonEntity(om.writeValueAsString(aggregate))
        performESRequest(request)
    }

    private fun readFromCache(id: UUID): CachedAggregate? {
        val request = Request("GET", "/$INDEX/_doc/${id}")
        val response = performESRequest(request) ?: return null
        val source = om.readTree(response).get("_source")
        return om.treeToValue(source, CachedAggregate::class.java)
    }

    fun deleteFromCache(id: UUID): String? {
        log.info("deleting from ES $id")
        val request = Request("DELETE", "/$INDEX/_doc/${id}")
        return performESRequest(request)
    }

    private fun performESRequest(request: Request): String? {
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

    // this one is ignored by graphql (but not jackson!), as this is used as a temp store after assembling the
    // data / reading from the cache, so that we can still filter according to the client's input
    @Ignore
    var _components: List<ComponentEntity>,

    var discountsAndSurcharges: DiscountsAndSurcharges // var so it can be improved, if it failed
) {
    fun isAnythingMissing(): Boolean {
        if(discountsAndSurcharges.failed) {
            return true
        }
        return false
    }

    constructor(): this(Contract(), emptyList(), DiscountsAndSurcharges(emptyList()))  // required by smallrye graphql
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
    var approvedBy: String?
) {
    constructor() : this(UUID.randomUUID(), LocalDateTime.MIN, LocalDateTime.MAX, ContractState.DRAFT, 0L,
        LocalDateTime.MIN, "", null, null, null, null, null, null) // required by smallrye graphql

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
        entity.approvedBy
    )
}

data class Component(
    var id: UUID,
    var parentId: UUID?,
    var componentDefinitionId: String,
    var configs: List<ConfigPair>,
    var productId: ProductId?
) {
    constructor() : this(UUID.randomUUID(), null, "", emptyList(), null) // required by smallrye graphql

    constructor(om: ObjectMapper, entity: ComponentEntity) : this(
        entity.id,
        entity.parentId,
        entity.componentDefinitionId,
        om.readValue<ArrayList<Configuration<*>>>(entity.configuration).map { ConfigPair(it.name, it.value.toString()) },
        entity.productId
    )
}

data class ConfigPair(var key: ConfigurableParameter, var value: String) {
    constructor(): this(ConfigurableParameter.VOLUME, "") // required by smallrye graphql
}

data class DiscountsAndSurcharges(
    var discountsSurcharges: List<DiscountSurcharge>,
    var failed: Boolean = false
) {
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
