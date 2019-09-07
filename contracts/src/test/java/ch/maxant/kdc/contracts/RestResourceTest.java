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
            .body("{\"contractNumber\":\"" + cn + "\",\"a\":\"1\"}")
            .post("/contracts")
            .then()
            .statusCode(200)
            .body("version", Matchers.is(0))
            .extract().response();
        String id = response.path("id");
        assertEquals(String.valueOf(cn), response.path("contractNumber"));

        // get list by contract number
        response = given()
            .when()
            .get("/contracts/versions/" + cn)
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].id", is(id))
            .body("[0].version", is(0))
            .body("[0].a", is("1"))
            .extract().response();
        assertEquals(String.valueOf(cn), response.path("[0].contractNumber"));

        // get by id
        response = given()
            .when()
            .get("/contracts/" + id)
            .then()
            .statusCode(200)
            .body("id", is(id))
            .body("version", is(0))
            .body("a", is("1"))
            .extract().response();
        assertEquals(String.valueOf(cn), response.path("contractNumber"));

        // update
        response = given()
            .when()
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
            .body("{\"contractNumber\":\"" + cn + "\", \"version\":0, \"id\": \"" + id + "\",\"a\":\"2\"}")
            .put("/contracts")
            .then()
            .statusCode(200)
            .body("id", Matchers.is(id))
            .body("a", Matchers.is("2"))
            .body("version", Matchers.is(1))
            .extract().response();
        assertEquals(String.valueOf(cn), response.path("contractNumber"));

        // update - optimistic lock exception
        given()
            .when()
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .body("{\"contractNumber\":\"" + cn + "\", \"version\":0, \"id\": \"" + id + "\",\"a\":\"3\"}")
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
    }
}