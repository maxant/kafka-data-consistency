package ch.maxant.kdc.mf.partners.adapter

import ch.maxant.kdc.mf.partners.entity.AddressEntity
import ch.maxant.kdc.mf.partners.entity.AddressType
import ch.maxant.kdc.mf.partners.entity.PartnerEntity
import ch.maxant.kdc.mf.partners.entity.PersonType
import com.fasterxml.jackson.databind.ObjectMapper
import org.eclipse.microprofile.faulttolerance.Retry
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
    lateinit var restClient: RestClient

    @Inject
    lateinit var om: ObjectMapper

    val log: Logger = Logger.getLogger(this.javaClass)

    @Retry(delay = 1000)
    fun createPartner(partner: PartnerEntity) {
        val request = Request(
                "PUT",
                "/partners/_doc/" + partner.id)
        val esPartner = EsPartner(partner)
        request.setJsonEntity(om.writeValueAsString(esPartner))
        performRequest(request)
        log.info("inserted partner ${partner.id} in elasticsearch")
    }

    private fun performRequest(request: Request) {
        try {
            val response = restClient.performRequest(request)
            // TODO tidy up exception handling. status code isnt returned if the server returns an error, eg 4xx coz of bad request
            val statusCode = response.statusLine.statusCode
            if(statusCode < 200 || statusCode >= 300) {
                throw WebApplicationException("failed to index partner in ES. ($statusCode) ${response.entity}")
            }
        } catch(e: ConnectException) {
            throw WebApplicationException("failed to index partner in ES. no connection", e)
        } catch(e: Exception) {
            if(e is WebApplicationException){
                throw e
            }
            throw WebApplicationException("failed to index partner in ES", e)
        }
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

