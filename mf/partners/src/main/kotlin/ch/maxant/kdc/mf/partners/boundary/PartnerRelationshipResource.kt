package ch.maxant.kdc.mf.partners.boundary

import ch.maxant.kdc.mf.library.MfValidationException
import ch.maxant.kdc.mf.library.doByHandlingValidationExceptions
import ch.maxant.kdc.mf.partners.entity.ForeignIdType
import ch.maxant.kdc.mf.partners.entity.PartnerEntity
import ch.maxant.kdc.mf.partners.entity.PartnerRelationshipEntity
import ch.maxant.kdc.mf.partners.entity.Role
import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Timed
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import org.jboss.logging.Logger
import java.time.LocalDateTime
import java.util.*
import javax.inject.Inject
import javax.persistence.EntityManager
import javax.validation.ValidationException
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import kotlin.collections.HashMap

@Path("/partner-relationships")
@Tag(name = "partner-relationships")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class PartnerRelationshipResource(
    @Inject
    var em: EntityManager
) {
    private val log = Logger.getLogger(this.javaClass)

    @GET
    @Operation(summary = "gets the latest partners for the given foreignId and role")
    @APIResponses(
            APIResponse(description = "a partner", responseCode = "200", content = [
                Content(mediaType = MediaType.APPLICATION_JSON,schema = Schema(type = SchemaType.ARRAY, implementation = PartnerRelationshipDetails::class))
            ])
    )
    @Path("/latestByForeignId/{foreignId}/{role}")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun latestByForeignIdAndRole(
            @Parameter(name = "idsOnly", description = "if true, then the result is an array of IDs rather than PartnerEntities")
            @QueryParam("idsOnly") idsOnly: Boolean = false,
            @Parameter(name = "foreignId", description = "the id of say the contract, to which the partner has a relationship")
            @PathParam("foreignId") foreignId: String,
            @Parameter(name = "role", description = "the role the partner plays in the relationship, or optionally just '*' in order to select all the latest relationships, regardless of role")
            @PathParam("role") roleString: String
    ): Response {
        val allRelationships = if(roleString == "*") {
            PartnerRelationshipEntity.Queries.selectByForeignId(em, foreignId)
        } else {
            PartnerRelationshipEntity.Queries.selectByForeignIdAndRole(em, foreignId, Role.valueOf(roleString))
        }
        val latest = HashMap<String, PartnerRelationshipEntity>()
        allRelationships.forEach { r ->
            val key = r.foreignId + r.role
            latest.computeIfAbsent(key) { r }
            latest.computeIfPresent(key) { _, existing -> if (existing.end > r.end) existing else r }
        }
        if(idsOnly) {
            return Response.ok(latest.values.map { PartnerRelationshipDetails(it.partnerId, null, it.start, it.end, it.role, it.foreignId) }).build()
        } else {
            val partnerIds = latest.map { it.value.partnerId }
            val partners = PartnerEntity.Queries.selectByIds(em, partnerIds)
            val results = latest.values.map {
                val partner = partners.find { p -> it.partnerId == p.id }
                PartnerRelationshipDetails(it.partnerId, partner, it.start, it.end, it.role, it.foreignId)
            }
            return Response.ok(results).build()
        }
    }

    @GET
    @Operation(summary = "gets the relationships that the given partner has to other entities")
    @APIResponses(
            APIResponse(description = "all relationships this partner has", responseCode = "200", content = [
                Content(mediaType = MediaType.APPLICATION_JSON,schema = Schema(type = SchemaType.ARRAY, implementation = PartnerRelationshipDetails::class))
            ])
    )
    @Path("/allByPartnerId/{partnerId}")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun allByPartnerId(
            @Parameter(name = "idsOnly", description = "if true, then the result is an array of IDs rather than PartnerEntities")
            @QueryParam("idsOnly") idsOnly: Boolean = false,
            @Parameter(name = "partnerId", description = "the id of the partner")
            @PathParam("partnerId") partnerId: UUID
    ): Response {

        val allRelationships = PartnerRelationshipEntity.Queries.selectByPartnerId(em, partnerId)
        if(idsOnly) {
            return Response.ok(allRelationships.map { PartnerRelationshipDetails(it.partnerId, null, it.start, it.end, it.role, it.foreignId) }).build()
        } else {
            val partnerIds = allRelationships.map { it.partnerId }.distinct()
            val partners = PartnerEntity.Queries.selectByIds(em, partnerIds)
            val results = allRelationships.map {
                val partner = partners.find { p -> it.partnerId == p.id }
                PartnerRelationshipDetails(it.partnerId, partner, it.start, it.end, it.role, it.foreignId)
            }
            return Response.ok(results).build()
        }
    }

    @GET
    @Operation(summary = "validates the cardinalitites")
    @APIResponses(
            APIResponse(responseCode = "200", content = [
                Content(mediaType = MediaType.APPLICATION_JSON,schema = Schema(implementation = Unit::class))
            ]),
            APIResponse(responseCode = "400", content = [
                Content(mediaType = MediaType.APPLICATION_JSON,schema = Schema(implementation = ValidationException::class))
            ])
    )
    @Path("/validate/{foreignId}")
    @Timed(unit = MetricUnits.MILLISECONDS)
    fun validate(
            @Parameter(name = "foreignId", description = "the id of say the contract, to which the partner has a relationship")
            @PathParam("foreignId") foreignId: String,
            @Parameter(name = "rolesThatCanBeMissing", description = "optional list of roles that are not important for the current validation")
            @QueryParam("rolesThatCanBeMissing") rolesThatCanBeMissing: List<Role>
    ): Response = doByHandlingValidationExceptions {
        log.info("validating relationships for foreignId $foreignId")

        val allRelationships = PartnerRelationshipEntity.Queries.selectByForeignId(em, foreignId)
        val latest = HashMap<String, PartnerRelationshipEntity>()
        allRelationships.forEach { r ->
            val key = r.foreignId + r.role
            latest.computeIfAbsent(key) { r }
            latest.computeIfPresent(key) { _, existing -> if (existing.end > r.end) existing else r }
        }

        val distinctTypes = latest.values.map { rel -> rel.role.foreignIdType }.distinct()

        checkAllRelationshipsHaveOneForeignIdType(distinctTypes)

        checkNumberOfRelationshipsMatchCardinality(distinctTypes.first(), latest, rolesThatCanBeMissing)

        checkForWrongCardinality(latest)

        log.info("validation complete")
        Response.ok().build()
    }

    private fun checkForWrongCardinality(latest: HashMap<String, PartnerRelationshipEntity>) {
        // TODO is the following superflous to the above? probably, but it doesnt check for required roles which dont yet exist!
        val relationshipsWithWrongCardinality = latest.values
                .groupBy { it.role }
                .filter { (role, relationships) -> relationships.size > role.minCardinality }
                .flatMap { it.value }
        log.info("relationshipsWithWrongCardinality $relationshipsWithWrongCardinality")
        if (relationshipsWithWrongCardinality.isNotEmpty()) {
            throw RelationshipsWithWrongCardinalityValidationException(
                    relationshipsWithWrongCardinality.map { WrongCardinality(it.role, it.partnerId) }
            )
        }
    }

    private fun checkNumberOfRelationshipsMatchCardinality(distinctType: ForeignIdType, latest: HashMap<String, PartnerRelationshipEntity>, rolesThatCanBeMissing: List<Role>) {
        // now check we have the right number of relationships
        Role.values()
                .filter { it.foreignIdType == distinctType }
                .filter { !rolesThatCanBeMissing.contains(it) }
                .forEach { role ->
                    val numOfRelationshipsInRole = latest.values.filter { it.role == role }.count()
                    if (numOfRelationshipsInRole < role.minCardinality) throw NotEnoughRelationshipsForForeignIdTypeValidationException(role)
                    if (numOfRelationshipsInRole > role.maxCardinality) throw TooManyRelationshipsForForeignIdTypeValidationException(role)
                }
    }

    private fun checkAllRelationshipsHaveOneForeignIdType(distinctTypes: List<ForeignIdType>): List<ForeignIdType> {
        // check we only have relationships belonging to one type of foreignId, e.g. contracts
        if (distinctTypes.isEmpty())
            throw NoRelationshipsFoundValidationException()
        if (distinctTypes.size != 1)
            throw NoSingleForeignIdTypeValidationException(distinctTypes)
        return distinctTypes
    }
}

data class PartnerRelationshipDetails(
        val partnerId: UUID,
        val partner: PartnerEntity?,
        val start: LocalDateTime,
        val end: LocalDateTime,
        val role: Role,
        val foreignId: String
)

class NoRelationshipsFoundValidationException: ValidationException()
class NoSingleForeignIdTypeValidationException(val typesPresent: List<ForeignIdType>): MfValidationException(typesPresent)
class RelationshipsWithWrongCardinalityValidationException(val relationShips: List<WrongCardinality>): MfValidationException(relationShips)
data class WrongCardinality(val role: Role, val partnerId: UUID)
class NotEnoughRelationshipsForForeignIdTypeValidationException(role: Role) : MfValidationException(role)
class TooManyRelationshipsForForeignIdTypeValidationException(role: Role) : MfValidationException(role)
