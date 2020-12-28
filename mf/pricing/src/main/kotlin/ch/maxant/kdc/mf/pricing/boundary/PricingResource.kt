package ch.maxant.kdc.mf.pricing.boundary

import ch.maxant.kdc.mf.pricing.definitions.Price
import ch.maxant.kdc.mf.pricing.entity.PriceEntity
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import java.time.LocalDateTime
import java.util.*
import javax.inject.Inject
import javax.persistence.EntityManager
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("/pricing")
@Tag(name = "pricing")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class PricingResource(
    @Inject
    public var em: EntityManager
) {

    @GET
    @Path("/countNotSameSyncTime/{contractId}/{syncTimestamp}")
    fun validateSyncTime(@PathParam("contractId") contractId: UUID, @PathParam("syncTimestamp") syncTimestamp: Long) =
            Response.ok(PriceEntity.Queries.countByContractIdAndNotSyncTimestamp(em, contractId, syncTimestamp)).build()

    @GET
    @Path("/totalPrice")
    fun totalPrice(@QueryParam("componentIds") componentIds: List<UUID>,
                   @QueryParam("dateTime") dateTime: LocalDateTime
    ): Response {
        val entities = PriceEntity.Queries.selectByComponentIdsAndDateTime(em, componentIds, dateTime)
        val price = entities.map{ it.price }.reduce{ acc, price -> acc.add(price) }
        val tax = entities.map{ it.tax }.reduce{ acc, tax -> acc.add(tax) }
        return Response.ok(Price(price, tax)).build()
    }
}