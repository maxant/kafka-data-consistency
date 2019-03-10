package ch.maxant.kdc.tasks;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

@Path("read")
@ApplicationScoped
public class TasksResource {

    Map<String, List<String>> tasks = new ConcurrentHashMap<>();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTasks(String businessKey) {
        return Response.ok(tasks.computeIfAbsent(businessKey, k -> new Vector<>())).build();
    }

    // TODO consume command topic
    // TODO publish event that tasks were created

}
