package ch.maxant.kdc.objects;

import io.smallrye.mutiny.Uni;
import io.vertx.reactivex.mysqlclient.MySQLPool;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.Arrays.asList;

@Path("objects")
public class ObjectResource {

    Logger logger = Logger.getLogger(ObjectResource.class.getName());

    @Inject
    MySQLPool client;

    @GET
    @Path("{id}")
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
}
