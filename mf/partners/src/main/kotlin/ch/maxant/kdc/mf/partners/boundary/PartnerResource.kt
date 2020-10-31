package ch.maxant.kdc.mf.partners.boundary

import ch.maxant.kdc.mf.partners.entity.PartnerEntity
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
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class PartnerResource(
    @Inject
    public var em: EntityManager
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

    @POST
    @Transactional
    @Produces(MediaType.TEXT_PLAIN)
    fun create(partner: PartnerEntity) =
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
