package ch.maxant.kdc.mf.contracts.adapter

import ch.maxant.kdc.mf.contracts.definitions.ComponentDefinition
import ch.maxant.kdc.mf.contracts.definitions.Configuration
import ch.maxant.kdc.mf.contracts.definitions.Product
import ch.maxant.kdc.mf.contracts.dto.Component
import ch.maxant.kdc.mf.contracts.dto.Draft
import ch.maxant.kdc.mf.contracts.entity.ContractState
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.readValue
import org.eclipse.microprofile.faulttolerance.Retry
import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Timed
import org.eclipse.microprofile.opentracing.Traced
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.elasticsearch.client.Request
import org.elasticsearch.client.RestClient
import org.jboss.logging.Logger
import java.math.BigDecimal
import java.net.ConnectException
import java.time.LocalDateTime
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.ws.rs.WebApplicationException

@ApplicationScoped
class ESAdapter {
    @Inject
    lateinit var om: ObjectMapper

    @Inject
    lateinit var sender: EsAdapterSender

    @Inject
    @Channel("contracts-es-out")
    lateinit var esOut: Emitter<String>

    val log: Logger = Logger.getLogger(this.javaClass)

    data class EsRequest(val context: EsRequestType, val method: String, val path: String, val json: String)

    enum class EsRequestType {
        CREATE, UPDATE_STATE, UPDATE_COMPONENTS
    }

    @Timed(unit = MetricUnits.MILLISECONDS)
    @Incoming("contracts-es-in")
    fun upload(payload: String) {
        try {
            val r = om.readValue<EsRequest>(payload)
            val request = Request(r.method, r.path)
            request.setJsonEntity(r.json)
            when(r.context) {
                EsRequestType.CREATE -> sender.create(request)
                EsRequestType.UPDATE_COMPONENTS -> sender.updateComponents(request)
                EsRequestType.UPDATE_STATE -> sender.updateState(request)
            }
            log.info("called elasticsearch with request $r")
        } catch(e: Exception) {
            // TODO DLT?
            log.error("CONES001 FAILED to send data to elastic. Will not be tried again. Please ensure it is entered manually. $payload", e)
        }
    }

    @Timed(unit = MetricUnits.MILLISECONDS)
    @Traced
    fun createDraft(draft: Draft, partnerId: UUID?) {
        val esContract = EsContract(draft, partnerId, draft.allComponents.map { ESComponent(it) })
        val r = EsRequest(EsRequestType.CREATE, "PUT", "/contracts/_doc/${draft.contract.id}", om.writeValueAsString(esContract))
        esOut.send(om.writeValueAsString(r))
    }

    @Timed(unit = MetricUnits.MILLISECONDS)
    @Traced
    fun updateComponents(contractId: UUID, allComponents: List<Component>) {
        /*
            {
              "script" : {
                "source": "ctx._source.counter += params.count",
                "lang": "painless",
                "params" : {
                  "count" : 4
                }
              }
            }
         */
        val components = om.readTree(om.writeValueAsString(allComponents.map { ESComponent(it) }.flatMap { it.toMetainfo() }))
        val params = om.createObjectNode()
        params.set<ObjectNode>("metainfo", components)

        val script = om.createObjectNode()
        script.set<ObjectNode>("params", params)
        script.put("source", "ctx._source.metainfo = params.metainfo")
        script.put("lang", "painless")

        val root = om.createObjectNode()
        root.set<ObjectNode>("script", script)

        val r = EsRequest(EsRequestType.UPDATE_COMPONENTS, "POST", "/contracts/_update/$contractId", om.writeValueAsString(root))
        // TODO use transactional outbox
        esOut.send(om.writeValueAsString(r))
    }

    @Timed(unit = MetricUnits.MILLISECONDS)
    @Traced
    fun updateState(contractId: UUID, newState: ContractState) {
        val params = om.createObjectNode()
        params.put("state", newState.toString())

        val script = om.createObjectNode()
        script.set<ObjectNode>("params", params)
        script.put("source", "ctx._source.state = params.state")
        script.put("lang", "painless")

        val root = om.createObjectNode()
        root.set<ObjectNode>("script", script)

        val r = EsRequest(EsRequestType.UPDATE_STATE, "POST", "/contracts/_update/$contractId", om.writeValueAsString(root))
        // TODO use transactional outbox
        esOut.send(om.writeValueAsString(r))
    }

    data class EsContract(val contractId: UUID, val partnerId: UUID?, val start: LocalDateTime, val end: LocalDateTime,
                          val state: ContractState, val metainfo: List<String>, val productId: String,
                          val createdAt: LocalDateTime, val createdBy: String, val totalPrice: BigDecimal = BigDecimal.ZERO) {
        constructor(draft: Draft, partnerId: UUID?, components: List<ESComponent>) : this(
                draft.contract.id,
                partnerId,
                draft.contract.start.withNano(0),
                draft.contract.end.withNano(0),
                draft.contract.contractState,
                components.flatMap { it.toMetainfo() },
                components.map { it.productId }.filter { it != null }.first()?: TODO(),
                draft.contract.createdAt.withNano(0),
                draft.contract.createdBy
        )
    }

    data class ESComponent(val componentDefinitionId: String, val configs: List<ESConfiguration>, val productId: String?) {
        constructor(comp: Component): this(comp.componentDefinitionId, comp.configs.map { ESConfiguration(it) }, comp.productId?.toString())

        fun toMetainfo(): List<String> = configs.map { "$componentDefinitionId ${it.name} ${it.value}" }
    }

    data class ESConfiguration(val name: String, val value: String) {
        constructor(config: Configuration<*>): this(config.name.toString(), config.value.toString())
    }
}

@ApplicationScoped
class EsAdapterSender {

    @Inject
    lateinit var restClient: RestClient

    @Retry(delay = 1000)
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun create(request: Request) {
        _performRequest(request)
    }

    @Retry(delay = 1000)
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun updateState(request: Request) {
        _performRequest(request)
    }

    @Retry(delay = 1000)
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun updateComponents(request: Request) {
        _performRequest(request)
    }

    fun _performRequest(request: Request) {
        try {
            val response = restClient.performRequest(request)
            // TODO tidy up exception handling. status code isnt returned if the server returns an error, eg 4xx coz of bad request
            val statusCode = response.statusLine.statusCode
            if(statusCode < 200 || statusCode >= 300) {
                throw WebApplicationException("failed to index contract in ES. ($statusCode) ${response.entity}")
            }
        } catch(e: ConnectException) {
            throw WebApplicationException("failed to index contract in ES. no connection", e)
        } catch(e: Exception) {
            if(e is WebApplicationException){
                throw e
            }
            throw WebApplicationException("failed to index contract in ES", e)
        }
    }

}
