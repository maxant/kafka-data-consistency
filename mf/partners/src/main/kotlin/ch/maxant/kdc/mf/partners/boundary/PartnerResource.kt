package ch.maxant.kdc.mf.partners.boundary

import ch.maxant.kdc.mf.partners.entity.AddressEntity
import ch.maxant.kdc.mf.partners.entity.PartnerEntity
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import org.eclipse.microprofile.openapi.annotations.tags.Tags
import java.net.URI
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
    var em: EntityManager
) {

    @GET
    @Path("/{id}")
    fun getById(@PathParam("id") id: UUID) =
        Response.ok(em.find(PartnerEntity::class.java, id)).build()!!

    @GET
    @Path("/search")
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
    @Produces(MediaType.TEXT_PLAIN)
    fun create(@Parameter(name = "partner", required = true) partner: PartnerEntity) =
        Response.created(URI("/partners/${partner.id}"))
                .entity(fun (): UUID{ em.persist(partner); return partner.id }())
                .build()!!
}

class DateOfBirth(dob: String?) {
    var date: LocalDate? = null
    init {
        date = if(dob == null) null else LocalDate.parse(dob)
    }
}
