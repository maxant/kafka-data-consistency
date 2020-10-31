package ch.maxant.kdc.mf.partners

import ch.maxant.kdc.mf.partners.entity.PersonType
import io.restassured.RestAssured.given
import io.restassured.builder.RequestSpecBuilder
import io.restassured.http.ContentType
import org.hamcrest.CoreMatchers.equalTo
import org.junit.jupiter.api.Test

class PartnersSVT {

    @Test
    fun testCreateAndGetAndSearch() {
        val spec = RequestSpecBuilder().setPort(8083).build()

        val body = """
            {
              "dob": "1980-10-31",
              "email": "john@smith.com",
              "firstName": "John",
              "lastName": "Smith",
              "phone": "0781231234",
              "type": "${PersonType.PERSON}"
            }
            """.trimIndent()

        // create
        val id: String = given(spec)
            .contentType(ContentType.JSON)
            .body(body)
            .log().all()
            .post("/partners")
            .then()
            .log().all()
            .statusCode(201)
            .extract()
            .body()
            .asString()

        // read
        given(spec)
            .contentType(ContentType.JSON)
            .log().all()
            .get("/partners/$id")
            .then()
            .log().all()
            .statusCode(200)
            .body("id", equalTo(id))
            .body("firstName", equalTo("John"))
            .body("lastName", equalTo("Smith"))
            .body("type", equalTo(PersonType.PERSON.toString()))
            .body("dob", equalTo("1980-10-31"))
            .body("email", equalTo("john@smith.com"))
            .body("phone", equalTo("0781231234"))

        // search by firstname
        given(spec)
            .contentType(ContentType.JSON)
            .log().all()
            .get("/partners/search?firstName=john") // lower case!
            .then()
            .log().all()
            .statusCode(200)
            .body("[0].firstName", equalTo("John"))
            .body("[0].lastName", equalTo("Smith"))
            .body("[0].type", equalTo(PersonType.PERSON.toString()))
            .body("[0].dob", equalTo("1980-10-31"))
            .body("[0].email", equalTo("john@smith.com"))
            .body("[0].phone", equalTo("0781231234"))

        // search by email
        given(spec)
            .contentType(ContentType.JSON)
            .log().all()
            .get("/partners/search?email=smith")
            .then()
            .log().all()
            .statusCode(200)
            .body("[0].firstName", equalTo("John"))
            .body("[0].lastName", equalTo("Smith"))
            .body("[0].type", equalTo(PersonType.PERSON.toString()))
            .body("[0].dob", equalTo("1980-10-31"))
            .body("[0].email", equalTo("john@smith.com"))
            .body("[0].phone", equalTo("0781231234"))

        // search by phone
        given(spec)
            .contentType(ContentType.JSON)
            .log().all()
            .get("/partners/search?phone=0781231234")
            .then()
            .log().all()
            .statusCode(200)
            .body("[0].firstName", equalTo("John"))
            .body("[0].lastName", equalTo("Smith"))
            .body("[0].type", equalTo(PersonType.PERSON.toString()))
            .body("[0].dob", equalTo("1980-10-31"))
            .body("[0].email", equalTo("john@smith.com"))
            .body("[0].phone", equalTo("0781231234"))

        // search by dob
        given(spec)
            .contentType(ContentType.JSON)
            .log().all()
            .get("/partners/search?dob=1980-10-31")
            .then()
            .log().all()
            .statusCode(200)
            .body("[0].firstName", equalTo("John"))
            .body("[0].lastName", equalTo("Smith"))
            .body("[0].type", equalTo(PersonType.PERSON.toString()))
            .body("[0].dob", equalTo("1980-10-31"))
            .body("[0].email", equalTo("john@smith.com"))
            .body("[0].phone", equalTo("0781231234"))

        // search by all
        given(spec)
            .contentType(ContentType.JSON)
            .log().all()
            .get("/partners/search?firstName=j&lastName=sm&dob=1980-10-31&email=com&phone=1234")
            .then()
            .log().all()
            .statusCode(200)
            .body("[0].firstName", equalTo("John"))
            .body("[0].lastName", equalTo("Smith"))
            .body("[0].type", equalTo(PersonType.PERSON.toString()))
            .body("[0].dob", equalTo("1980-10-31"))
            .body("[0].email", equalTo("john@smith.com"))
            .body("[0].phone", equalTo("0781231234"))
    }
}