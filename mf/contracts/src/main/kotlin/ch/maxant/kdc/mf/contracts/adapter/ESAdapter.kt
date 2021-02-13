package ch.maxant.kdc.mf.contracts.adapter

import ch.maxant.kdc.mf.contracts.definitions.ComponentDefinition
import ch.maxant.kdc.mf.contracts.definitions.Configuration
import ch.maxant.kdc.mf.contracts.dto.Component
import ch.maxant.kdc.mf.contracts.dto.Draft
import ch.maxant.kdc.mf.contracts.entity.ContractState
import com.fasterxml.jackson.databind.ObjectMapper
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

    data class EsRequest(val method: String, val path: String, val json: String)

    @Timed(unit = MetricUnits.MILLISECONDS)
    @Incoming("contracts-es-in")
    fun upload(payload: String) {
        try {
            val r = om.readValue<EsRequest>(payload)
            val request = Request(r.method, r.path)
            request.setJsonEntity(r.json)
            sender.performRequest(request)
            log.info("called elasticsearch with request $r")
        } catch(e: Exception) {
            // TODO DLT?
            log.error("CONES001 FAILED to send data to elastic. Will not be tried again. Please ensure it is entered manually. $payload")
        }
    }

    @Timed(unit = MetricUnits.MILLISECONDS)
    @Traced
    fun createOffer(draft: Draft, partnerId: UUID?) {
        val esContract = EsContract(draft, partnerId, flatten(draft.pack))
        val r = EsRequest("PUT", "/contracts/_doc/${draft.contract.id}", om.writeValueAsString(esContract))
        esOut.send(om.writeValueAsString(r))
    }

    private fun flatten(defn: ComponentDefinition, result: MutableList<ESComponent> = mutableListOf()): MutableList<ESComponent> {
        result.add(ESComponent(defn))
        defn.children.forEach { flatten(it, result) }
        return result
    }

    @Timed(unit = MetricUnits.MILLISECONDS)
    @Traced
    fun updateOffer(contractId: UUID, allComponents: List<Component>) {
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
        val root = om.createObjectNode()
        val script = om.createObjectNode()
        val params = om.createObjectNode()
        root.replace("script", script)
        script.put("source", "ctx._source.metainfo = params.metainfo")
        script.put("lang", "painless")
        script.replace("params", params)
        val components = om.readTree(om.writeValueAsString(allComponents.map { ESComponent(it) }.flatMap { it.toMetainfo() }))
        params.replace("components", components)
    }

    @Timed(unit = MetricUnits.MILLISECONDS)
    @Traced
    fun updateOffer(contractId: UUID, newState: ContractState) {
        val request = Request(
                "POST",
                "/contracts/_update/$contractId")
        val root = om.createObjectNode()
        val script = om.createObjectNode()
        val params = om.createObjectNode()
        root.replace("script", script)
        script.put("source", "ctx._source.state = params.state")
        script.put("lang", "painless")
        script.replace("params", params)
        params.put("state", newState.toString())

        val r = EsRequest("POST", "/contracts/_update/$contractId", om.writeValueAsString(root))
        // TODO use transactional outbox
        esOut.send(om.writeValueAsString(r))
    }

    data class EsContract(val partnerId: UUID?, val start: LocalDateTime, val end: LocalDateTime, val state: ContractState,
                          val metainfo: List<String>, val totalPrice: BigDecimal = BigDecimal.ZERO) {
        constructor(draft: Draft, partnerId: UUID?, components: List<ESComponent>) : this(
                partnerId,
                draft.contract.start.withNano(0),
                draft.contract.end.withNano(0),
                draft.contract.contractState,
                components.flatMap { it.toMetainfo() }
        )
    }

    data class ESComponent(val componentDefinitionId: String, val configs: List<ESConfiguration>) {
        constructor(defn: ComponentDefinition): this(defn.componentDefinitionId, defn.configs.map { ESConfiguration(it) })
        constructor(comp: Component): this(comp.componentDefinitionId, comp.configs.map { ESConfiguration(it) })

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
    fun performRequest(request: Request) {
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
