package ch.maxant.kdc.mf.partners.boundary

import ch.maxant.kdc.mf.partners.entity.ForeignIdType
import ch.maxant.kdc.mf.partners.entity.PartnerEntity
import ch.maxant.kdc.mf.partners.entity.PartnerRelationshipEntity
import ch.maxant.kdc.mf.partners.entity.Role
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.tags.Tag
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
    public var em: EntityManager
) {

    @GET
    @Operation(summary = "gets the latest matching partners")
    @APIResponses(
            APIResponse(description = "a partner", responseCode = "201", content = [
                Content(mediaType = MediaType.APPLICATION_JSON,schema = Schema(type = SchemaType.ARRAY, implementation = PartnerRelationshipDetails::class))
            ])
    )
    @Path("/latest/{foreignId}/{role}")
    fun latestByForeignIdAndRole(
            @Parameter(name = "idsOnly", description = "if true, then the result is an array of IDs rather than PartnerEntities")
            @QueryParam("idsOnly") idsOnly: Boolean = false,
            @Parameter(name = "foreignId", description = "the id of say the contract, to which the partner has a relationship")
            @PathParam("foreignId") foreignId: String,
            @Parameter(name = "role", description = "the role the partner plays in the relationship")
            @PathParam("role") role: Role
    ): Response {

        val allRelationships = PartnerRelationshipEntity.Queries.selectByForeignIdAndRole(em, foreignId, role)
        val latest = HashMap<String, PartnerRelationshipEntity>()
        allRelationships.forEach { r ->
            val key = r.foreignId + r.role
            latest.computeIfAbsent(key) { _ -> r }
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
    fun validate(
            @Parameter(name = "foreignId", description = "the id of say the contract, to which the partner has a relationship")
            @PathParam("foreignId") foreignId: String
    ): Response {

        val allRelationships = PartnerRelationshipEntity.Queries.selectByForeignId(em, foreignId)
        val latest = HashMap<String, PartnerRelationshipEntity>()
        allRelationships.forEach { r ->
            val key = r.foreignId + r.role
            latest.computeIfAbsent(key) { _ -> r }
            latest.computeIfPresent(key) { _, existing -> if (existing.end > r.end) existing else r }
        }

        val distinctTypes = latest.values.map { rel -> rel.role.foreignIdType }.distinct()
        if(distinctTypes.count() != 1)
            throw NoSingleForeignIdTypeValidationException(distinctTypes)

        val relationshipsWithWrongCardinality = latest.values
                .groupBy { it.role }
                .filter { (role, relationships)  -> relationships.size > role.cardinality }
                .flatMap { it.value }
        if(relationshipsWithWrongCardinality.isNotEmpty()) {
            throw RelationshipsWithWrongCardinalityValidationException(
                relationshipsWithWrongCardinality.map { WrongCardinality(it.role, it.partnerId) }
            )
        }
        return Response.ok().build()
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

class NoSingleForeignIdTypeValidationException(val typesPresent: List<ForeignIdType>): ValidationException()
class RelationshipsWithWrongCardinalityValidationException(val relationShips: List<WrongCardinality>): ValidationException()
data class WrongCardinality(val role: Role, val partnerId: UUID)
