package ch.maxant.kdc.mf.contracts.boundary.query

import ch.maxant.kdc.mf.contracts.adapter.DiscountsSurchargesAdapter
import ch.maxant.kdc.mf.contracts.definitions.ConfigurableParameter
import ch.maxant.kdc.mf.contracts.definitions.Configuration
import ch.maxant.kdc.mf.contracts.definitions.ProductId
import ch.maxant.kdc.mf.contracts.entity.ComponentEntity
import ch.maxant.kdc.mf.contracts.entity.ContractEntity
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.module.kotlin.readValue
import io.smallrye.graphql.api.Context
import org.eclipse.microprofile.graphql.*
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.jboss.logging.Logger
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.inject.Inject
import javax.persistence.EntityManager

/**
 * <pre>
query {
    #aggregate { => uses a default value instead of providing one as follows:
    aggregate(id: "77c1917d-2061-42e6-9631-78f10cbae161") {

        # corresponds to the tolerant reader pattern, in that you only
        # fetch data you really want - you MUST specify every field
        # that you want to read

        contract {
            id
            createdAt
            createdAtFormattedByServer
            contractState
            createdAtFormattedByClient(pattern: "MMMM dd, YYYY")
            discountsAddedInNewLocationInTree {
                definitionId
            }

            # not currently working in quarkus due to class loading issues in named queries
            components(definitionIdFilter:"Milk") {
                id
                parentId
                productId
                configs {key, value}
                componentDefinitionId
            }
        }

        # the following two were attempts to load data generically, but
        # GraphQL doesnt seem to support that or wildcards
        #
        # discountsSurchargesArrayNode
        # discountsSurchargesString
        #
        # so we load this data and have to provide the schema / write DTOs
        discountsSurchargesDto {
            componentId addedManually definitionId value
        }
    }
}

* </pre>
* see https://download.eclipse.org/microprofile/microprofile-graphql-1.0.3/microprofile-graphql.html#graphql_and_rest
* http://localhost:8080/graphql/schema.graphql
* http://localhost:8080/graphql-ui
*/
@GraphQLApi
class ContractQueryResource(
    @Inject var em: EntityManager,
    @Inject var om: ObjectMapper,
    @Inject var context: Context
) {
    @Inject
    @RestClient // bizarrely this doesnt work with constructor injection
    lateinit var discountsSurchargesAdapter: DiscountsSurchargesAdapter
TODO add lazy loading and caching in ES and use Events to evict it from ES
    private val log = Logger.getLogger(this.javaClass)

    @Query("aggregate")
    @Description("Get a contract by it's ID")
    fun findContractById(@Name("id") @DefaultValue("77c1917d-2061-42e6-9631-78f10cbae161") id: UUID): ContractAggregate {
        log.info("getting contract $id with context.arguments ${context.arguments.map { "${it.key}->${it.value}" }}")
        val discountsSurchargesArrayNode = discountsSurchargesAdapter.getByContractIdAsArrayNode(id)
        val discountsSurchargesString = discountsSurchargesAdapter.getByContractIdAsString(id)
        val discountsSurchargesDto = discountsSurchargesAdapter.getByContractIdAsDto(id)
        return ContractAggregate(em.find(ContractEntity::class.java, id), discountsSurchargesArrayNode,
            discountsSurchargesString, discountsSurchargesDto)
    }

    // adds a field called "discountsAddedInNewLocationInTree" to the entity
    fun discountsAddedInNewLocationInTree(@Source contract: ContractEntity): List<DiscountSurchargeEntity> =
        discountsSurchargesAdapter.getByContractIdAsDto(contract.id).filter { it.value < BigDecimal.ZERO }

    // adds a formatted field to the entity
    @DateFormat(value = "dd MMM yyyy")
    fun createdAtFormattedByServer(@Source contract: ContractEntity): LocalDateTime =
        contract.createdAt

    // adds a field to the entity, using the pattern supplied by the client
    fun createdAtFormattedByClient(@Name("pattern") pattern: String, @Source contract: ContractEntity): String =
        contract.createdAt.format(DateTimeFormatter.ofPattern(pattern))

    @Query("components")
    fun components(@Source contract: ContractEntity, @Name("definitionIdFilter") definitionIdFilter: String): List<Component> {
        val entities = em.createQuery("select c from ComponentEntity c where c.contractId = :contractId")
            .setParameter("contractId", contract.id)
            .resultList as List<ComponentEntity> // avoid named query, and cast because of this: https://quarkusio.zulipchat.com/#narrow/stream/187030-users/topic/Hibernate.2FGraphQL.20SRGQL012000.3A.20Data.20Fetching.20Error
        return entities
            .filter { Regex(definitionIdFilter).matches((it).componentDefinitionId) }
            .map { Component(om, it) }
    }
}

@Type("Aggregate")
data class ContractAggregate(
    @NonNull val contract: ContractEntity?, // if the server dishes up null, the client gets an error!

    // not necessary: @ToScalar(Scalar.String::class)
    val discountsSurchargesArrayNode: ArrayNode,

    val discountsSurchargesString: String,
    val discountsSurchargesDto: List<DiscountSurchargeEntity>
)

data class DiscountSurchargeEntity(
    var id: UUID,
    var contractId: UUID,
    var componentId: UUID,
    var definitionId: String,
    var value: BigDecimal,
    var syncTimestamp: Long,
    var addedManually: Boolean
)

data class Component(
    val id: UUID,
    val parentId: UUID?,
    val componentDefinitionId: String,
    val configs: List<ConfigPair>,
    val productId: ProductId?
) {
    constructor(om: ObjectMapper, entity: ComponentEntity) : this(
        entity.id,
        entity.parentId,
        entity.componentDefinitionId,
        om.readValue<ArrayList<Configuration<*>>>(entity.configuration).map { ConfigPair(it.name, it.value.toString()) },
        entity.productId
    )
}

data class ConfigPair(val key: ConfigurableParameter, val value: String)
