package ch.maxant.kdc.tasks;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Vector;

@Path("read")
@ApplicationScoped
public class TasksResource {

    @Inject
    Model model;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTasks(String businessKey) {
        return Response.ok(model.getTasks().computeIfAbsent(businessKey, k -> new Vector<>())).build();
    }
}
