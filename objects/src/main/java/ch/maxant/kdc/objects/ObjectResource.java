package ch.maxant.kdc.objects;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.BackPressureStrategy;
import io.vertx.mutiny.core.Vertx;
import io.vertx.reactivex.mysqlclient.MySQLPool;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.Arrays.asList;

@Path("objects")
public class ObjectResource {

    private static final Map<String, SubscriberModel> SUBSCRIBERS = new ConcurrentHashMap<>();

    Logger logger = Logger.getLogger(ObjectResource.class.getName());

    @Inject
    MySQLPool client;

    @Inject
    Vertx vertx;

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> get(@PathParam("id") UUID id) {
        long start = System.currentTimeMillis();
        logger.info("starting request for id " + id);

        CompletableFuture<Response> cf = new CompletableFuture();

        client.query("select * from flyway_schema_history", r -> {
            // from here on in it is no longer async, but all in memory
            if (r.succeeded()) {
                logger.info("got " + r.result().rowCount() + " rows with the following columns: " + r.result().columnsNames());
                StringBuilder sb = new StringBuilder();
                r.result().forEach(row -> {
                    logger.info("row: " + row);
                    sb.append(row);
                });
                cf.complete(Response.accepted(asList(new AnObject(id, sb.toString()))).build());
            } else {
                logger.log(Level.SEVERE, "failed to get db results", r.cause());
                cf.complete(Response.serverError().entity(r.cause().getMessage()).build());
            }
        });

        cf.thenAccept(r -> logger.info("finished request for id " + id + " in " + (System.currentTimeMillis() - start) + " ms"));

        return Uni.createFrom().completionStage(cf);
    }

    /**
     * allows a caller to subscribe to changes of a certain type of object
     */
    @GET
    @Path("/changes/{subscriberId}")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<AnObject> changes(@PathParam("subscriberId") String subscriberId, @QueryParam("type") List<String> type) {
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
    public Response emit() {
        logger.info("emitting to " + SUBSCRIBERS.size() + " subscribers");
        SUBSCRIBERS.values().forEach(sm ->
                sm.emit(new AnObject(UUID.randomUUID(), "anObject"))
        );
        return Response.noContent().build();
    }
}
