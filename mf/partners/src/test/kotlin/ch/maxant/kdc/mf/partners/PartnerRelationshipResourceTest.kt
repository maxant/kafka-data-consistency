package ch.maxant.kdc.mf.partners;

import ch.maxant.kdc.mf.library.TestUtils
import ch.maxant.kdc.mf.partners.boundary.PartnerRelationshipDetails
import ch.maxant.kdc.mf.partners.boundary.PartnerRelationshipResource
import ch.maxant.kdc.mf.partners.entity.PartnerEntity
import ch.maxant.kdc.mf.partners.entity.PartnerRelationshipEntity
import ch.maxant.kdc.mf.partners.entity.PersonType
import ch.maxant.kdc.mf.partners.entity.Role
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.hamcrest.CoreMatchers.equalTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import javax.inject.Inject
import javax.persistence.EntityManager
import javax.transaction.Transactional

@QuarkusTest
@Transactional
class PartnerRelationshipResourceTest {

    @Inject
    lateinit var sut: PartnerRelationshipResource

    @Inject
    lateinit var em: EntityManager

    @Test
    fun test() {
        val p1id = UUID.randomUUID()
        val p2id = UUID.randomUUID()
        val p3id = UUID.randomUUID()
        val r1id = UUID.randomUUID()
        val r2id = UUID.randomUUID()
        val r3id = UUID.randomUUID()
        val foreignId = "contract1"
        TestUtils.flushed(em) {
            // contract holder
            em.persist(PartnerEntity(p1id, "John", "Smith", PersonType.PERSON, LocalDate.parse("1961-04-23"), "john@maxant.co.uk", "+41713234556"))

            // sales rep 1
            em.persist(PartnerEntity(p2id, "Jane", "Smith", PersonType.PERSON, LocalDate.parse("1960-03-31"), "jane@maxant.co.uk", "+41713234555"))

            // sales rep 2
            em.persist(PartnerEntity(p3id, "Janet", "Smith", PersonType.PERSON, LocalDate.parse("1962-12-05"), "janet@maxant.co.uk", "+41713234554"))

            em.persist(PartnerRelationshipEntity(r1id, p1id, foreignId,
                    LocalDateTime.parse("2020-01-01T00:00:00.000"), LocalDateTime.parse("9999-12-31T23:59:59.999"),
                    Role.CONTRACT_HOLDER))
            em.persist(PartnerRelationshipEntity(r2id, p2id, foreignId,
                    LocalDateTime.parse("2020-01-01T00:00:00.000"), LocalDateTime.parse("2020-12-31T23:59:59.999"),
                    Role.SALES_REP))
            em.persist(PartnerRelationshipEntity(r3id, p3id, foreignId,
                    LocalDateTime.parse("2021-01-01T00:00:00.000"), LocalDateTime.parse("9999-12-31T23:59:59.999"),
                    Role.SALES_REP))
        }

        // when - contract holder
        var response = sut.latestByForeignIdAndRole(false, foreignId, Role.CONTRACT_HOLDER)

        // then - contract holder
        var relationships = response.entity as List<PartnerRelationshipDetails>
        assertEquals(1, relationships.size)
        assertEquals(foreignId, relationships[0].foreignId)
        assertEquals(p1id, relationships[0].partnerId)
        assertEquals(Role.CONTRACT_HOLDER, relationships[0].role)
        assertEquals(p1id, relationships[0].partner!!.id)

        // when - sales rep
        response = sut.latestByForeignIdAndRole(false, foreignId, Role.SALES_REP)

        // then - sales rep
        relationships = response.entity as List<PartnerRelationshipDetails>
        assertEquals(1, relationships.size)
        assertEquals(foreignId, relationships[0].foreignId)
        assertEquals(p3id, relationships[0].partnerId)
        assertEquals(Role.SALES_REP, relationships[0].role)
        assertEquals(p3id, relationships[0].partner!!.id)

        // when/then via rest => TODO doesnt work without a commit. how to do a commit when we have to mark the method as transactional?
        /*
        given()
            .contentType(ContentType.JSON)
            .log().all()
            .get("/partner-relationships/latest/$foreignId/${Role.CONTRACT_HOLDER}")
            .then()
            .statusCode(200)
            .log().all()
            .body("[0].foreignId", equalTo(foreignId))
            .body("[0].partnerId", equalTo(p1id.toString()))
            .body("[0].role", equalTo(Role.CONTRACT_HOLDER.toString()))
            .body("[0].partner.id", equalTo(p1id.toString()))
         */
    }

}