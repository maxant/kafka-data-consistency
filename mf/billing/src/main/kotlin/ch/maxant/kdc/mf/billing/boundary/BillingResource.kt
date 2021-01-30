package ch.maxant.kdc.mf.billing.boundary

import ch.maxant.kdc.mf.billing.control.BillingService
import ch.maxant.kdc.mf.billing.definitions.ProductId
import ch.maxant.kdc.mf.billing.entity.BillsEntity
import ch.maxant.kdc.mf.library.MessageBuilder
import com.fasterxml.jackson.databind.ObjectMapper
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import javax.inject.Inject
import javax.persistence.EntityManager
import javax.transaction.Transactional
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("/billing")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class BillingResource(
    @Inject
    var om: ObjectMapper,

    @Inject
    var em: EntityManager,

    @Inject
    var billingService: BillingService
) {
    @GET
    @Path("/{id}")
    fun getById(@PathParam("id") id: UUID) =
        Response.ok(em.find(BillsEntity::class.java, id)).build()

    @POST
    @Path("/byContractId")
    fun getByContractIds(@PathParam("contractIds") contractIds: List<UUID>) =
        Response.ok(BillsEntity.Queries.selectByContractIds(em, contractIds)).build()

    @PUT
    @Path("/recurring/{from}")
    @Operation(summary = "select all contracts that need billing and do it")
    fun billRecurring(@Parameter(name = "from", required = true) @PathParam("from") from: String,
                      @Parameter(name = "maxSizeOfGroup", required = false) @QueryParam("maxSizeOfGroup") maxSizeOfGroup: Int?): Response {
        val job = billingService.startRecurringBilling(LocalDate.parse(from), maxSizeOfGroup?:50)
        return Response.accepted(job).build()
    }

    @DELETE
    @Path("/all")
    @Operation(summary = "delete all bills - only useful for testing!")
    @Transactional
    fun deleteAllBills(): Response {
        val numBills = em.createQuery("delete from BillsEntity").executeUpdate()
        val numContracts = em.createQuery("delete from BilledToEntity").executeUpdate()
        return Response.ok(Deleted(numBills, numContracts)).build()
    }

    data class ApprovedContract(val contract: ContractDto, val productId: ProductId)
    data class Deleted(val numBills: Int, val numContracts: Int)
}

