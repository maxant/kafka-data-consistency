package ch.maxant.kdc.mf.contracts.adapter

import ch.maxant.kdc.mf.contracts.definitions.ComponentDefinition
import ch.maxant.kdc.mf.contracts.definitions.Configuration
import ch.maxant.kdc.mf.contracts.dto.Component
import ch.maxant.kdc.mf.contracts.dto.Draft
import ch.maxant.kdc.mf.contracts.entity.ContractState
import com.fasterxml.jackson.databind.ObjectMapper
import org.eclipse.microprofile.faulttolerance.Retry
import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Timed
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
    lateinit var restClient: RestClient

    @Inject
    lateinit var om: ObjectMapper

    val log: Logger = Logger.getLogger(this.javaClass)

    @Retry(delay = 1000)
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun createOffer(draft: Draft, partnerId: UUID?) {
        val request = Request(
                "PUT",
                "/contracts/_doc/" + draft.contract.id)
        val esContract = EsContract(draft, partnerId, flatten(draft.pack))
        request.setJsonEntity(om.writeValueAsString(esContract))
        performRequest(request)
        log.info("inserted contract ${draft.contract.id} in elasticsearch")
    }

    private fun flatten(defn: ComponentDefinition, result: MutableList<ESComponent> = mutableListOf()): MutableList<ESComponent> {
        result.add(ESComponent(defn))
        defn.children.forEach { flatten(it, result) }
        return result
    }

    @Retry(delay = 1000)
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun updateOffer(contractId: UUID, allComponents: List<Component>) {
        val request = Request(
                "POST",
                "/contracts/_update/$contractId")
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
        script.put("source", "ctx._source.components = params.components")
        script.put("lang", "painless")
        script.replace("params", params)
        val components = om.readTree(om.writeValueAsString(allComponents.map { ESComponent(it) }))
        params.replace("components", components)
        request.setJsonEntity(om.writeValueAsString(root))
        performRequest(request)
        log.info("updated components of contract $contractId in elasticsearch")
    }

    @Retry(delay = 1000)
    @Timed(unit = MetricUnits.MILLISECONDS)
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
        request.setJsonEntity(om.writeValueAsString(root))
        performRequest(request)
        log.info("updated status of contract $contractId in elasticsearch")
    }

    private fun performRequest(request: Request) {
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

    data class EsContract(val partnerId: UUID?, val start: LocalDateTime, val end: LocalDateTime, val state: ContractState,
                          val components: List<ESComponent>, val totalPrice: BigDecimal = BigDecimal.ZERO) {
        constructor(draft: Draft, partnerId: UUID?, components: List<ESComponent>) : this(
                partnerId,
                draft.contract.start.withNano(0),
                draft.contract.end.withNano(0),
                draft.contract.contractState,
                components
        )
    }

    data class ESComponent(val componentDefinitionId: String, val configs: List<ESConfiguration>) {
        constructor(defn: ComponentDefinition): this(defn.componentDefinitionId, defn.configs.map { ESConfiguration(it) })
        constructor(comp: Component): this(comp.componentDefinitionId, comp.configs.map { ESConfiguration(it) })
    }

    data class ESConfiguration(val name: String, val value: String) {
        constructor(config: Configuration<*>): this(config.name.toString(), config.value.toString())
    }
}

