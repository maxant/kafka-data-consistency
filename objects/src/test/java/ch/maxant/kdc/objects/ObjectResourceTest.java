package ch.maxant.kdc.objects;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.ws.rs.core.HttpHeaders;
import java.util.Random;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@QuarkusTest
public class ObjectResourceTest {

    @Inject
    ObjectResource objectResource;

    private static final Random RANDOM = new Random();

    @BeforeAll
    public static void setup() {
        RestAssured.baseURI = "http://localhost";
        //RestAssured.baseURI = "http://kdc.objects.maxant.ch";
        RestAssured.port = 8086; // note this must match quarkus.http.test-port in application.properties
        //RestAssured.port = 80;
    }

    @Test
    public void test() throws InterruptedException {
        String xUsername = "ak-" + RANDOM.nextInt(1_000_000);
        Response response =
                given()
                        .header("x-username", xUsername)
                        .body("{\"name\":\"anObj\"}")
                        .contentType("application/json")
                        .log()
                        .all()
                        .when()
                        .put("/objects/v2")
                        .then()
                        .log()
                        .all()
                        .statusCode(200)
                        .extract()
                        .response();
        String responseText = response.asString();
        String[] split = responseText.split(":");
        assertEquals(xUsername, split[0]);
        if(!split[1].equals(split[2])) {
            System.out.println("got one with differing threads! " + responseText);
        }

        // and now call it without rest assured, by directly calling the injected cdi bean

        HttpHeaders headers = mock(HttpHeaders.class);
        when(headers.getHeaderString("x-username")).thenReturn(xUsername);

        CountDownLatch l = new CountDownLatch(1); // use a CDL because of the async nature of the response
        CompletionStage<javax.ws.rs.core.Response> r = objectResource.putV2(headers);
        r.thenAccept(resp -> {
            assertTrue(resp.getEntity().toString().startsWith(xUsername));
            System.out.println("done 2");
            l.countDown();
        });

        l.await();
        System.out.println("done 1");
    }
}
