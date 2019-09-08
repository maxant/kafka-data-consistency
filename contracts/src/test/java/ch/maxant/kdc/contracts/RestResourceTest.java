package ch.maxant.kdc.contracts;

import io.restassured.response.Response;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

//@QuarkusTest TODO what does this do exactly?
public class RestResourceTest {

    @Test
    public void test() {
        given()
          .when().get("/contracts/dbversion")
          .then()
             .statusCode(200)
             .body(is("1.001"));
    }

    @Test
    public void all() {
        long cn = System.currentTimeMillis(); // unique enough

        // check nothing exists
        given()
            .when()
            .get("/contracts/versions/" + cn)
            .then()
            .statusCode(200)
            .body("$", hasSize(0));

        // create
        Response response = given()
            .when()
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
            .body("{\"contractNumber\":\"" + cn + "\",\"from\":\"2019-01-01T00:00:00.000\",\"to\":\"9999-12-31T23:59:59.999\",\"a\":\"1\"}")
            .post("/contracts/HomeContentsInsurance")
            .then()
            .statusCode(200)
            .body("version", Matchers.is(0))
            .body("product.name", is("HomeContentsInsurance"))
            .extract().response();
        String id = response.path("id");
        assertEquals(String.valueOf(cn), response.path("contractNumber"));
        assertEquals("0", response.path("product.discount").toString());
        assertEquals("100000.0", response.path("product.totalInsuredValue").toString());

        // get list by contract number
        response = given()
            .when()
            .get("/contracts/versions/" + cn)
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].id", is(id))
            .body("[0].version", is(0))
            .body("[0].product", Matchers.nullValue())
            .extract().response();
        assertEquals(String.valueOf(cn), response.path("[0].contractNumber"));
        assertEquals("2019-01-01T00:00:00", response.path("[0].from"));
        assertEquals("9999-12-31T23:59:59.999", response.path("[0].to"));

        // get by id
        response = given()
            .when()
            .get("/contracts/" + id)
            .then()
            .statusCode(200)
            .body("id", is(id))
            .body("version", is(0))
            .body("product.name", is("HomeContentsInsurance"))
            .extract().response();
        assertEquals(String.valueOf(cn), response.path("contractNumber"));
        assertEquals("2019-01-01T00:00:00", response.path("from"));
        assertEquals("9999-12-31T23:59:59.999", response.path("to"));
        assertEquals("0", response.path("product.discount"));
        assertEquals("100000.00", response.path("product.totalInsuredValue"));

        // update
        response = given()
            .when()
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
            .body("{\"contractNumber\":\"" + cn + "\", \"version\":0, \"id\": \"" + id + "\",\"from\":\"2019-01-01T00:00:00.000\",\"to\":\"9999-12-31T23:59:59.999\",\"a\":\"2\"}")
            .put("/contracts")
            .then()
            .statusCode(200)
            .body("id", Matchers.is(id))
            .body("version", Matchers.is(1))
            .body("product.name", is("HomeContentsInsurance"))
            .extract().response();
        assertEquals(String.valueOf(cn), response.path("contractNumber"));
        assertEquals("2019-01-01T00:00:00", response.path("from"));
        assertEquals("9999-12-31T23:59:59.999", response.path("to"));

        // update - optimistic lock exception
        given()
            .when()
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
            .body("{\"contractNumber\":\"" + cn + "\", \"version\":0, \"id\": \"" + id + "\",\"from\":\"2019-01-01T00:00:00.000\",\"to\":\"9999-12-31T23:59:59.999\",\"a\":\"3\"}")
            .put("/contracts")
            .then()
            .statusCode(409)
            .log().all()
        ;

        // get list by contract number
        response = given()
                .when()
                .get("/contracts/versions/" + cn)
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", is(id))
                .body("[0].version", is(1))
                .body("[0].a", is("2"))
                .extract().response();
        assertEquals(String.valueOf(cn), response.path("[0].contractNumber"));
        assertEquals("2019-01-01T00:00:00", response.path("[0].from"));
        assertEquals("9999-12-31T23:59:59.999", response.path("[0].to"));
    }
}