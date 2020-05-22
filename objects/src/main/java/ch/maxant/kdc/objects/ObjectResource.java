package ch.maxant.kdc.objects;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.BackPressureStrategy;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.WebClient;
import io.vertx.reactivex.mysqlclient.MySQLPool;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.context.ThreadContext;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.jboss.resteasy.annotations.SseElementType;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.Collections.singletonList;

@Path("objects")
public class ObjectResource {

    private static final Map<String, SubscriberModel> SUBSCRIBERS = new ConcurrentHashMap<>();

    Logger logger = Logger.getLogger(ObjectResource.class.getName());

    @Inject
    MySQLPool client;

    private WebClient webClient;

    @Inject
    Vertx vertx;

    @Inject
    ThreadContext threadContext;

    @Inject
    ManagedExecutor managedExecutor;

    @Inject
    MyContext myContext;

    @PostConstruct
    void init() {
        this.webClient = WebClient.create(vertx,
                new WebClientOptions().setDefaultHost("localhost")
                        .setDefaultPort(8086).setSsl(false).setTrustAll(true));
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Counted(name = "countGetObject", description = "Counts how many times the getObject method has been invoked")
    @Timed(name = "timeGetObject", description = "Times how long it takes to invoke the getObject  method", unit = MetricUnits.MILLISECONDS)
    public Uni<Response> getObject(@PathParam("id") UUID id) {
        long start = System.currentTimeMillis();
        logger.info("starting request for id " + id);

        CompletableFuture<Response> cf = new CompletableFuture<>();

        client.query("select * from flyway_schema_history", r -> {
            // from here on in it is no longer async, but all in memory
            if (r.succeeded()) {
                logger.info("got " + r.result().rowCount() + " rows with the following columns: " + r.result().columnsNames());
                StringBuilder sb = new StringBuilder();
                r.result().forEach(row -> {
                    logger.info("row: " + row);
                    sb.append(row);
                });
                cf.complete(Response.accepted(singletonList(new AnObject(id, sb.toString()))).build());
            } else {
                logger.log(Level.SEVERE, "failed to get db results", r.cause());
                cf.complete(Response.serverError().entity(r.cause().getMessage()).build());
            }
        });

        cf.thenAccept(r -> logger.info("finished request for id " + id + " in " + (System.currentTimeMillis() - start) + " ms"));

        return Uni.createFrom().completionStage(cf);
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Counted(name = "countPutObject")
    @Timed(name = "timePutObject", unit = MetricUnits.MILLISECONDS)
    public CompletionStage<Response> put(@Context HttpHeaders headers, AnObject objectToUpsert) {

        myContext.setUsername(headers.getHeaderString("x-username"));
        myContext.setThread1(Thread.currentThread().getName());

        logger.info("processing request for user " + myContext.getUsername());

        return threadContext.withContextCapture(webClient
                .get("/objects/1987bb7d-e02c-4691-a0b3-0dfbaab990be")
                .putHeader("x-token", myContext.getUsername())
                .send()
                .subscribeAsCompletionStage())
            .thenApplyAsync(response -> {
                logger.info("got response and have username " + myContext.getUsername());
                return Response.ok(myContext.getUsername() + ":" + myContext.getThread1() + ":" + Thread.currentThread().getName()).build();
            }, managedExecutor);
    }

    /**
     * allows a caller to subscribe to changes of a certain type of object
     */
    @GET
    @Path("/changes/{subscriberId}")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @SseElementType(MediaType.APPLICATION_JSON)
    @Counted(name = "countSubscribeChanges")
    public Multi<JsonObject> changes(@PathParam("subscriberId") String subscriberId, @QueryParam("type") List<String> type) {
        SubscriberModel subscriberModel = SUBSCRIBERS.computeIfAbsent(subscriberId, k -> new SubscriberModel());
        subscriberModel.setId(subscriberId);
        return Multi.createFrom()
                .emitter(e -> {
                    subscriberModel.setEmitter(e);
                    e.onTermination(() -> {
                        logger.info("Removing subscriber " + subscriberId);
                        e.complete();
                        SUBSCRIBERS.remove(subscriberId);

                        // even though the above works nicely, there is an exception logged by quarkus, afterwards.
                        // see https://stackoverflow.com/questions/61694510/how-to-handle-a-closedchannelexception-on-a-reactive-streams-http-connection-clo
                        // see https://github.com/quarkusio/quarkus/issues/9194
                    });
                }, BackPressureStrategy.ERROR);
    }

    /**
     * test method in order to emit an object to ALL subscribers
     */
    @GET
    @Path("/emit")
    @Produces(MediaType.APPLICATION_JSON)
    @Counted(name = "countEmit")
    @Timed(name = "timeEmit", unit = MetricUnits.MILLISECONDS)
    public Uni<Response> emit() {
        logger.info("handling emit request...");

        // an example of calling a downstream service with rest, reactively, non blockingly
        return webClient.get("/api/fruit/Apple")
                .send()
                .onItem().apply(resp -> {
                    logger.info("got response from downstream service " + resp.bodyAsString());
                    if (resp.statusCode() == Response.Status.OK.getStatusCode()) {
                        return resp.bodyAsJsonObject();
                    } else {
                        return new JsonObject()
                                .put("code", resp.statusCode())
                                .put("message", resp.bodyAsString())
                                .put("family", "error");
                    }
                })
                .onItem().invoke(json -> {
                    logger.info("emitting to " + SUBSCRIBERS.size() + " subscribers");
                    SUBSCRIBERS.values().forEach(sm ->
                        sm.emit(JsonObject.mapFrom(new AnObject(UUID.randomUUID(), json.getString("family"))))
                    );
                }).map(json -> Response.ok(json).build());
    }
}
