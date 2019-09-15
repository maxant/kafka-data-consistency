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
    public void version() {
        given()
          .when().get("/contracts/dbversion")
          .then()
             .statusCode(200)
             .body(is("1.003"));
    }

    @Test
    public void all() {
        long cn = System.currentTimeMillis(); // unique enough

        System.out.println("creating contract " + cn);

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
            .body("{\"contractNumber\":\"" + cn + "\",\"from\":\"2019-01-01T00:00:00.000\",\"to\":\"9999-12-31T23:59:59.999\"}")
            .post("/contracts/BuildingInsurance")
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
            .extract().response();
        assertEquals(String.valueOf(cn), response.path("[0].contractNumber"));
        assertEquals("2019-01-01T00:00:00", response.path("[0].from"));
        assertEquals("9999-12-31T23:59:59.999", response.path("[0].to"));

        // update
        response = given()
            .when()
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
            .body("{\"contractNumber\":\"" + cn + "\", \"version\":0, \"id\": \"" + id + "\",\"from\":\"2019-01-15T00:00:00.000\",\"to\":\"9999-12-31T23:59:59.999\"}")
            .put("/contracts")
            .then()
            .statusCode(200)
            .body("id", Matchers.is(id))
            .body("version", Matchers.is(1))
            .extract().response();
        assertEquals(String.valueOf(cn), response.path("contractNumber"));
        assertEquals("2019-01-15T00:00:00", response.path("from"));
        assertEquals("9999-12-31T23:59:59.999", response.path("to"));

        // update - optimistic lock exception
        given()
            .when()
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
            .body("{\"contractNumber\":\"" + cn + "\", \"version\":0, \"id\": \"" + id + "\",\"from\":\"2019-01-30T00:00:00.000\",\"to\":\"9999-12-31T23:59:59.999\"}")
            .put("/contracts")
            .then()
            .statusCode(409)
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
                .extract().response();
        assertEquals(String.valueOf(cn), response.path("[0].contractNumber"));
        assertEquals("2019-01-15T00:00:00", response.path("[0].from"));
        assertEquals("9999-12-31T23:59:59.999", response.path("[0].to"));

        // get details including product instance
        response = given()
                .when()
                .get("/contracts/" + cn + "/2019-02-01")
                .then()
                .statusCode(200)
                .extract().response();
        id = response.path("id");
        assertEquals(String.valueOf(cn), response.path("contractNumber"));
        assertEquals("2019-01-15T00:00:00", response.path("from"));
        assertEquals("9999-12-31T23:59:59.999", response.path("to"));
        assertEquals(Integer.valueOf(1), response.path("version"));
        assertEquals(id, response.path("product.contractId"));
        assertEquals("2019-01-15T00:00:00", response.path("product.from"));
        assertEquals("9999-12-31T23:59:59.999", response.path("product.to"));
        assertEquals(Integer.valueOf(1), response.path("product.version"));
        assertEquals("0.0", response.path("product.discount").toString());
        assertEquals("BuildingInsurance", response.path("product.name"));
        assertEquals("100.0", response.path("product.indexValue").toString());

        // update
        given()
                .when()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .body("{\"contractNumber\": \"" + cn + "\",\"from\":\"2020-01-01T00:00:00.000\",\"newIndexValue\":102.00}")
                .put("/contracts/indexValue")
                .then()
                .log().all()
                .statusCode(204);

        // old product instance version
        response = given()
                .when()
                .get("/contracts/" + cn + "/2019-12-31")
                .then()
                .statusCode(200)
                .extract().response();
        assertEquals(String.valueOf(cn), response.path("contractNumber"));
        assertEquals("2019-01-15T00:00:00", response.path("from"));
        assertEquals("9999-12-31T23:59:59.999", response.path("to"));
        assertEquals(Integer.valueOf(1), response.path("version"));
        assertEquals(id, response.path("product.contractId"));
        assertEquals("2019-01-15T00:00:00", response.path("product.from"));
        assertEquals("2019-12-31T23:59:59.999", response.path("product.to"));
        assertEquals(Integer.valueOf(2), response.path("product.version"));
        assertEquals("0.0", response.path("product.discount").toString());
        assertEquals("BuildingInsurance", response.path("product.name"));
        assertEquals("100.0", response.path("product.indexValue").toString());

        // new product instance version
        response = given()
                .when()
                .get("/contracts/" + cn + "/2020-01-01")
                .then()
                .statusCode(200)
                .extract().response();
        assertEquals(String.valueOf(cn), response.path("contractNumber"));
        assertEquals("2019-01-15T00:00:00", response.path("from"));
        assertEquals("9999-12-31T23:59:59.999", response.path("to"));
        assertEquals(Integer.valueOf(1), response.path("version"));
        assertEquals(id, response.path("product.contractId"));
        assertEquals("2020-01-01T00:00:00", response.path("product.from"));
        assertEquals("9999-12-31T23:59:59.999", response.path("product.to"));
        assertEquals(Integer.valueOf(0), response.path("product.version"));
        assertEquals("0.0", response.path("product.discount").toString());
        assertEquals("BuildingInsurance", response.path("product.name"));
        assertEquals("102.0", response.path("product.indexValue").toString());

        // update #2
        given()
                .when()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .body("{\"contractNumber\": \"" + cn + "\",\"from\":\"2019-07-01T00:00:00.000\",\"newIndexValue\":103.00}")
                .put("/contracts/indexValue")
                .then()
                .log().all()
                .statusCode(204);

        // first product instance version
        response = given()
                .when()
                .get("/contracts/" + cn + "/2019-06-30")
                .then()
                .statusCode(200)
                .extract().response();
        assertEquals(String.valueOf(cn), response.path("contractNumber"));
        assertEquals("2019-01-15T00:00:00", response.path("from"));
        assertEquals("9999-12-31T23:59:59.999", response.path("to"));
        assertEquals(Integer.valueOf(1), response.path("version"));
        assertEquals(id, response.path("product.contractId"));
        assertEquals("2019-01-15T00:00:00", response.path("product.from"));
        assertEquals("2019-06-30T23:59:59.999", response.path("product.to"));
        assertEquals(Integer.valueOf(3), response.path("product.version"));
        assertEquals("0.0", response.path("product.discount").toString());
        assertEquals("BuildingInsurance", response.path("product.name"));
        assertEquals("100.0", response.path("product.indexValue").toString());

        // second product instance version
        response = given()
                .when()
                .get("/contracts/" + cn + "/2019-09-01")
                .then()
                .statusCode(200)
                .extract().response();
        assertEquals(String.valueOf(cn), response.path("contractNumber"));
        assertEquals("2019-01-15T00:00:00", response.path("from"));
        assertEquals("9999-12-31T23:59:59.999", response.path("to"));
        assertEquals(Integer.valueOf(1), response.path("version"));
        assertEquals(id, response.path("product.contractId"));
        assertEquals("2019-07-01T00:00:00", response.path("product.from"));
        assertEquals("2019-12-31T23:59:59.999", response.path("product.to"));
        assertEquals(Integer.valueOf(0), response.path("product.version"));
        assertEquals("0.0", response.path("product.discount").toString());
        assertEquals("BuildingInsurance", response.path("product.name"));
        assertEquals("103.0", response.path("product.indexValue").toString());

        // third product instance version
        response = given()
                .when()
                .get("/contracts/" + cn + "/2020-01-01")
                .then()
                .statusCode(200)
                .extract().response();
        assertEquals(String.valueOf(cn), response.path("contractNumber"));
        assertEquals("2019-01-15T00:00:00", response.path("from"));
        assertEquals("9999-12-31T23:59:59.999", response.path("to"));
        assertEquals(Integer.valueOf(1), response.path("version"));
        assertEquals(id, response.path("product.contractId"));
        assertEquals("2020-01-01T00:00:00", response.path("product.from"));
        assertEquals("9999-12-31T23:59:59.999", response.path("product.to"));
        assertEquals(Integer.valueOf(0), response.path("product.version"));
        assertEquals("0.0", response.path("product.discount").toString());
        assertEquals("BuildingInsurance", response.path("product.name"));
        assertEquals("103.0", response.path("product.indexValue").toString());

        response = given()
                .when()
                .get("/contracts/product/versions/" + cn)
                .then()
                .statusCode(200)
                .body("$", hasSize(3))
                .extract().response();
        assertEquals(String.valueOf(cn), response.path("[0].contract.contractNumber"));
        assertEquals("2019-01-15T00:00:00", response.path("[0].contract.from"));
        assertEquals("9999-12-31T23:59:59.999", response.path("[0].contract.to"));
        assertEquals("2019-01-15T00:00:00", response.path("[0].from"));
        assertEquals("2019-06-30T23:59:59.999", response.path("[0].to"));
        assertEquals("0.0", response.path("[0].discount").toString());
        assertEquals("BuildingInsurance", response.path("[0].name"));
        assertEquals("100.0", response.path("[0].indexValue").toString());
        assertEquals((String)response.path("[0].contract.id"), response.path("[0].contractId"));

        assertEquals(String.valueOf(cn), response.path("[1].contract.contractNumber"));
        assertEquals("2019-01-15T00:00:00", response.path("[1].contract.from"));
        assertEquals("9999-12-31T23:59:59.999", response.path("[1].contract.to"));
        assertEquals("2019-07-01T00:00:00", response.path("[1].from"));
        assertEquals("2019-12-31T23:59:59.999", response.path("[1].to"));
        assertEquals("0.0", response.path("[1].discount").toString());
        assertEquals("BuildingInsurance", response.path("[1].name"));
        assertEquals("103.0", response.path("[1].indexValue").toString());
        assertEquals((String)response.path("[1].contract.id"), response.path("[1].contractId"));

        assertEquals(String.valueOf(cn), response.path("[2].contract.contractNumber"));
        assertEquals("2019-01-15T00:00:00", response.path("[2].contract.from"));
        assertEquals("9999-12-31T23:59:59.999", response.path("[2].contract.to"));
        assertEquals("2020-01-01T00:00:00", response.path("[2].from"));
        assertEquals("9999-12-31T23:59:59.999", response.path("[2].to"));
        assertEquals("0.0", response.path("[2].discount").toString());
        assertEquals("BuildingInsurance", response.path("[2].name"));
        assertEquals("103.0", response.path("[2].indexValue").toString());
        assertEquals((String)response.path("[2].contract.id"), response.path("[2].contractId"));

        // check everything belongs to same contract version
        assertEquals((String)response.path("[0].contractId"), response.path("[1].contractId"));
        assertEquals((String)response.path("[1].contractId"), response.path("[2].contractId"));
    }
}