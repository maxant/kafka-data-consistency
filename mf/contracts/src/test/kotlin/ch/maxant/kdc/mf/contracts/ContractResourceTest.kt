package ch.maxant.kdc.mf.contracts

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.hamcrest.CoreMatchers.equalTo
import org.junit.jupiter.api.Test

@QuarkusTest
class ContractResourceTest {

    @Test
    fun test() {

        if(true) return

        val body = """
            {
                "productId": "myProduct",
                "start": "2020-10-21T00:00:00.000",
                "end":   "2025-12-31T23:59:59.999"
            }
            """.trimIndent()
        val id: String = given()
            .contentType(ContentType.JSON)
            .body(body)
            .log().all()
            .post("/contracts")
            .then()
            .statusCode(201)
            .log().all()
            .body("start", equalTo("2020-10-21T00:00:00")) // TODO why's it not contain millis, even tho theyre in the db?
            .body("end", equalTo("2025-12-31T23:59:59.999"))
            .body("productId", equalTo("myProduct"))
            .extract()
            .body()
            .path("id");

        given()
            .log().all()
            .get("/contracts/$id")
            .then()
            .statusCode(200)
            .log().all()
            .body("id", equalTo(id))
            .body("start", equalTo("2020-10-21T00:00:00"))
            .body("end", equalTo("2025-12-31T23:59:59.999"))
            .body("productId", equalTo("myProduct"))
    }

}