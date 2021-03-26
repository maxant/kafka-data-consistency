package ch.maxant.kdc.objects;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ContextPropagationSVT {

    private static final Random RANDOM = new Random();
    private static final AtomicInteger TOTAL_CALLS = new AtomicInteger();

    @BeforeAll
    public static void setup() {
        RestAssured.baseURI = "http://localhost";
        //RestAssured.baseURI = "http://kdc.objects.maxant.ch";
        RestAssured.port = 8086;
        //RestAssured.port = 80;
    }

    @Test
    public void testContextPropagation() {
        ExecutorService executorService = new ThreadPoolExecutor(50, 50, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue(10), new ThreadPoolExecutor.CallerRunsPolicy());
        while (true) {
            executorService.submit(() -> {
                _testContextPropagation();
            });
        }
    }

    private void _testContextPropagation() {
        // curl -v -X PUT -H 'x-username:AKT2' -H 'Content-Type:application/json' localhost:8086/objects/ -d '{}'
        // should return the given username and we're looking for an example where the two threads are different ones
        String xUsername = "ak-" + RANDOM.nextInt(1_000_000);
        long start = System.currentTimeMillis();
        Response response =
                given()
                    .header("x-username", xUsername)
                    .body("{\"name\":\"anObj\"}")
                    .contentType("application/json")
                    //.log()
                    //.all()
                .when()
                    .put("/objects/v2")
                .then()
                    //.log()
                    //.all()
                    .statusCode(200)
                    .extract()
                    .response();
        System.out.println(TOTAL_CALLS.incrementAndGet() + " - result in " + (System.currentTimeMillis() - start) + "ms");
        String responseText = response.asString();
        String[] split = responseText.split(":");
        assertEquals(xUsername, split[0]);
        if(!split[1].equals(split[2])) {
            System.out.println("got one with differing threads! " + responseText);
        }
    }
}
