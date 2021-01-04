package ch.maxant.kdc.mf.partners.adapter

import ch.maxant.kdc.mf.partners.entity.AddressEntity
import ch.maxant.kdc.mf.partners.entity.AddressType
import ch.maxant.kdc.mf.partners.entity.PartnerEntity
import ch.maxant.kdc.mf.partners.entity.PersonType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.eclipse.microprofile.faulttolerance.Retry
import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Timed
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.elasticsearch.client.Request
import org.elasticsearch.client.RestClient
import org.jboss.logging.Logger
import java.net.ConnectException
import java.time.LocalDate
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
    @Channel("partners-es-out")
    lateinit var esOut: Emitter<String>

    val log: Logger = Logger.getLogger(this.javaClass)

    data class EsRequest(val method: String, val path: String, val json: String)

    @Timed(unit = MetricUnits.MILLISECONDS)
    @Incoming("partners-es-in")
    fun upload(payload: String) {
        try {
            val r = om.readValue<EsRequest>(payload)
            val request = Request(r.method, r.path)
            request.setJsonEntity(r.json)
            sender.performRequest(request)
            log.info("called elasticsearch with request $r")
        } catch(e: Exception) {
            // TODO DLT?
            log.error("PARES001 FAILED to send data to elastic. Will not be tried again. Please ensure it is entered manually. $payload")
        }
    }

    @Timed(unit = MetricUnits.MILLISECONDS)
    fun createPartner(partner: PartnerEntity) {
        val esPartner = EsPartner(partner)
        val r = EsRequest("PUT", "/partners/_doc/${partner.id}", om.writeValueAsString(esPartner))
        esOut.send(om.writeValueAsString(r))
    }

    data class EsPartner(val partnerId: UUID, val firstName: String, val lastName: String, val dob: LocalDate,
                         val email: String, val phone: String, val type: PersonType, val address: List<ESAddress>) {
        constructor(partner: PartnerEntity) : this(
                partner.id,
                partner.firstName,
                partner.lastName,
                partner.dob,
                partner.email,
                partner.phone,
                partner.type,
                partner.addresses?.map { ESAddress(it) }.orEmpty()
        )
    }

    data class ESAddress(val street: String, val houseNumber: String, val postcode: String, val city: String,
                         val state: String, val country: String, val type: AddressType) {
        constructor(address: AddressEntity): this(
                address.street,
                address.houseNumber,
                address.postcode,
                address.city,
                address.state,
                address.country,
                address.type)
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
