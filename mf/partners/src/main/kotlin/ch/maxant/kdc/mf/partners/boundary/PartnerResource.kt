package ch.maxant.kdc.mf.partners.boundary

import ch.maxant.kdc.mf.library.doByHandlingValidationExceptions
import ch.maxant.kdc.mf.partners.adapter.ESAdapter
import ch.maxant.kdc.mf.partners.entity.AddressEntity
import ch.maxant.kdc.mf.partners.entity.PartnerEntity
import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Timed
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import org.jboss.logging.Logger
import java.time.LocalDate
import java.util.*
import javax.inject.Inject
import javax.persistence.EntityManager
import javax.transaction.Transactional
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("/partners")
@Tag(name = "partners")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class PartnerResource(
    @Inject
    var em: EntityManager,

        @Inject
        var esAdapter: ESAdapter
) {
    val log: Logger = Logger.getLogger(this.javaClass)

    @GET
    @Path("/{id}")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun getById(@PathParam("id") id: UUID) =
        Response.ok(em.find(PartnerEntity::class.java, id)).build()!!

    @GET
    @Path("/search")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun search(
            @QueryParam("firstName") firstName: String?,
            @QueryParam("lastName") lastName: String?,
            @QueryParam("dob") dob: DateOfBirth?,
            @QueryParam("email") email: String?,
            @QueryParam("phone") phone: String?
    ) =
            Response.ok(
                    PartnerEntity.Queries.selectByFirstNameOrLastNameOrDobOrEmailOrPhone(
                            em, firstName, lastName, dob?.date, email, phone
                    )
            ).build()!!

    @GET
    @Operation(summary = "gets all addresses of the partner")
    @Path("/addresses/{partnerId}")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun getAddresses(
            @Parameter(name = "partnerId") @PathParam("partnerId") partnerId: UUID
    ) =
            Response.ok(
                    AddressEntity.Queries.selectByPartnerId(em, partnerId)
            ).build()!!

    @POST
    @Operation(summary = "Create a partner", description = "descr")
    @APIResponses(
            APIResponse(description = "a partner", responseCode = "201", content = [
                Content(mediaType = MediaType.APPLICATION_JSON, schema = Schema(implementation = PartnerEntity::class))
            ])
    )
    @Transactional
    @Produces(MediaType.APPLICATION_JSON)
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun create(@Parameter(name = "partner", required = true) partner: PartnerEntity) = doByHandlingValidationExceptions {

        log.info("creating new partner with id ${partner.id}")

        partner.addresses?.forEach { it.partner = partner } // ensure references are setup

        em.persist(partner)

        // TODO use transactional outbox. or just use a command via kafka, since once its in there, we have a retry. we need to subscribe to it as we dont
        // have any infrastructure to send kafka to ES
        esAdapter.createPartner(partner)

        Response.status(Response.Status.CREATED).entity(partner).build()
    }
}

class DateOfBirth(dob: String?) {
    var date: LocalDate? = null
    init {
        date = if(dob == null) null else LocalDate.parse(dob)
    }
}
