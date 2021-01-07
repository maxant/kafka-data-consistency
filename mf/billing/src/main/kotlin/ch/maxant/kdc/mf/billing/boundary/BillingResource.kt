package ch.maxant.kdc.mf.billing.boundary

import ch.maxant.kdc.mf.billing.definitions.ProductId
import ch.maxant.kdc.mf.library.MessageBuilder
import com.fasterxml.jackson.databind.ObjectMapper
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import java.time.LocalDateTime
import java.util.*
import javax.inject.Inject
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
    var messageBuilder: MessageBuilder
) {
    @Inject
    @Channel("contracts-event-bus-out")
    lateinit var contractsEventBus: Emitter<String>

    @GET
    @Path("/{id}")
    fun getById(@PathParam("id") id: UUID) =
        Response.ok("id").build()

    @POST
    @Path("/testNewContract")
    fun testNewContract(): Response {
        val contractId = UUID.randomUUID()
        val contract = ApprovedContract(ContractDto(contractId,
                LocalDateTime.now().plusDays(2),
                LocalDateTime.now().plusYears(1)), ProductId.COOKIES_MILKSHAKE)
        contractsEventBus.send(messageBuilder.build(contractId.toString(), contract, event = "APPROVED_CONTRACT"))
        return Response.ok(contractId).build()
    }

    data class ApprovedContract(val contract: ContractDto, val productId: ProductId)
}

