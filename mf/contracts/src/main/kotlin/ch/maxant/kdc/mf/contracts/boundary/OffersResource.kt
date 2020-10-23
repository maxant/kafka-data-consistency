package ch.maxant.kdc.mf.contracts.boundary

import ch.maxant.kdc.mf.contracts.definitions.ContractDefinition
import ch.maxant.kdc.mf.contracts.dto.OfferRequest
import ch.maxant.kdc.mf.contracts.entity.ContractEntity
import ch.maxant.kdc.mf.contracts.entity.Status
import java.net.URI
import javax.inject.Inject
import javax.persistence.EntityManager
import javax.transaction.Transactional
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import java.util.*


@Path("/offers")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class OffersResource(
    @Inject
    public var em: EntityManager
) {

    @Operation(summary = "Create an offer", description = "descr")
    @POST
    @Transactional
    fun create(
            @Parameter()
            offerRequest: OfferRequest): Response {

        val start = offerRequest.start.atStartOfDay()
        val contractDefinition = ContractDefinition.find(offerRequest.productId, start)
        val end = start.plusDays(contractDefinition.defaultDurationDays)

        val contract = ContractEntity(UUID.randomUUID(), offerRequest.productId, start, end, Status.DRAFT)
        em.persist(contract)



        return Response.created(URI.create("/${contract.id}")).entity(contract).build()
    }

}