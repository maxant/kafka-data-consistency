package ch.maxant.kdc.mf.billing.boundary

import ch.maxant.kdc.mf.billing.control.BillingService
import ch.maxant.kdc.mf.billing.control.StreamService
import ch.maxant.kdc.mf.billing.definitions.ProductId
import ch.maxant.kdc.mf.billing.entity.BillsEntity
import ch.maxant.kdc.mf.library.MessageBuilder
import com.fasterxml.jackson.databind.ObjectMapper
import io.quarkus.narayana.jta.runtime.TransactionConfiguration
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
    var billingStreamApplication: BillingStreamApplication,

    @Inject
    var streamService: StreamService,

    @Inject
    var billingService: BillingService
) {
    @GET
    @Path("/findByBillingId/{id}")
    fun getById(@PathParam("id") id: UUID) =
        Response.ok(em.find(BillsEntity::class.java, id)).build()

    @GET
    @Path("/findByContractId")
    fun findByContractIds(@QueryParam("contractIds") contractIds: List<UUID>) =
        Response.ok(BillsEntity.Queries.selectByContractIds(em, contractIds)).build()

    @PUT
    @Path("/recurring/{from}")
    @Operation(summary = "select all contracts that need billing and do it")
    fun billRecurring(@Parameter(name = "from", required = true) @PathParam("from") from: String,
                      @Parameter(name = "maxSizeOfGroup", required = false) @QueryParam("maxSizeOfGroup") maxSizeOfGroup: Int?): Response {
        val job = billingService.startRecurringBilling(LocalDate.parse(from), maxSizeOfGroup?:100)
        return Response.accepted(job).build()
    }

    @PUT
    @Path("/retry/{groupId}/{processStep}")
    @Operation(summary = "retry a group which failed")
    fun retryGroup(@Parameter(name = "groupId", required = true) @PathParam("groupId") groupId: String,
                   @Parameter(name = "processStep", required = true) @PathParam("processStep") processStep: String): Response {
        val group = om.readValue(billingStreamApplication.getGlobalGroup(groupId), GroupEntity::class.java).group
        if(group.failedProcessStep == null) return Response.status(Response.Status.BAD_REQUEST).entity("group didnt fail").build()
        val newGroupId = UUID.randomUUID()
        val newGroup = Group(group.jobId, newGroupId, group.contracts, nextProcessStep = BillingProcessStep.valueOf(processStep), started = LocalDateTime.now(), failedGroupId = UUID.fromString(groupId))
        streamService.sendGroup(newGroup)
        return Response.ok(newGroupId).build()
    }

    @DELETE
    @Path("/{when}")
    @Operation(summary = "delete bills - only useful for testing! either 'all' or 'today'")
    @Transactional
    @TransactionConfiguration(timeout = 600) // 10 minutes, since with larger data sets we can have problems
    fun deleteBills(@Parameter(name = "when") @PathParam("when") `when`: String): Response {
        val numBills = if(`when` == "today") {
            em.createQuery("delete from BillsEntity where start >= :start").setParameter("start", LocalDate.now()).executeUpdate()
        } else {
            em.createQuery("delete from BillsEntity").executeUpdate()
        }
        val numContracts = if(`when` == "today") {
            em.createQuery("update BilledToEntity b set b.billedTo = :date").setParameter("date", LocalDate.now().minusDays(1)).executeUpdate()
        } else {
            em.createQuery("delete from BilledToEntity").executeUpdate()
        }
        return Response.ok(Deleted(numBills, numContracts)).build()
    }

    data class ApprovedContract(val contract: ContractDto, val productId: ProductId)
    data class Deleted(val numBills: Int, val numContracts: Int)
}
