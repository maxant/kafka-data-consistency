package ch.maxant.kdc.mf.pricing.boundary

import ch.maxant.kdc.mf.pricing.definitions.Price
import ch.maxant.kdc.mf.pricing.entity.PriceEntity
import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Timed
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import java.lang.IllegalStateException
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
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun validateSyncTime(@PathParam("contractId") contractId: UUID, @PathParam("syncTimestamp") syncTimestamp: Long) =
            Response.ok(PriceEntity.Queries.countByContractIdAndNotSyncTimestamp(em, contractId, syncTimestamp)).build()

    @GET
    @Path("/totalPrice")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun totalPrice(@QueryParam("componentIds") componentIds: List<UUID>,
                   @QueryParam("dateTime") dateTimeString: String
    ): Response {
        val entities = PriceEntity.Queries.selectByComponentIdsAndDateTime(em, componentIds, LocalDateTime.parse(dateTimeString))
        if(entities.isEmpty()) throw IllegalStateException("no prices found for componentIds $componentIds and date $dateTimeString")
        val price = entities.map{ it.price }.reduce{ acc, price -> acc.add(price) }
        val tax = entities.map{ it.tax }.reduce{ acc, tax -> acc.add(tax) }
        return Response.ok(Price(price, tax)).build()
    }

    @GET
    @Path("/prices/{contractId}")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun priceByContractId(@PathParam("contractId") contractId: UUID,
                          @QueryParam("dateTime") dateTimeString: String
    ) = Response.ok(PriceEntity.Queries.selectByContractIdAndDateTime(em, contractId, LocalDateTime.parse(dateTimeString))).build()
}