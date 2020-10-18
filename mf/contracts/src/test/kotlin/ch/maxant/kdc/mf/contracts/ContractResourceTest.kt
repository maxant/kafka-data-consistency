package ch.maxant.kdc.mf.contracts

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.`is`
import org.junit.jupiter.api.Test

@QuarkusTest
class ContractResourceTest {

    @Test
    fun testHelloEndpoint() {
        given()
          .`when`().get("/contracts")
          .then()
             .statusCode(200)
             .body(`is`("hello"))
    }

}