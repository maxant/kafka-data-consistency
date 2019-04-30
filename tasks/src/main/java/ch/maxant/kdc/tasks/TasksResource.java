package ch.maxant.kdc.tasks;

import ch.maxant.kdc.library.KafkaAdapter;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collection;
import java.util.List;
import java.util.Vector;

import static ch.maxant.kdc.tasks.TasksRecordHandler.TASK_CREATED_EVENT_TOPIC;
import static java.util.stream.Collectors.toList;

@Path("tasks")
@ApplicationScoped
public class TasksResource {

    @Inject
    Model model;

    @Inject
    KafkaAdapter kafka;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTasks(String businessKey) {
        List<String> tasks;
        if(businessKey == null || businessKey.trim().isEmpty()) {
            tasks = model.getTasks().values().stream().flatMap(Collection::stream).collect(toList());
        } else {
            tasks = model.getTasks().computeIfAbsent(businessKey, k -> new Vector<>());
        }
        return Response.ok(tasks).build();
    }

    /** THIS METHOD IS JUST FOR DEMO PURPOSES - DELETE FOR PRODUCTION */
    @DELETE
    public Response delete() {
        model.getTasks().clear();
        kafka.publishEvent(TASK_CREATED_EVENT_TOPIC, null, "deleted-all");
        return Response.ok().build();
    }

    /** THIS METHOD IS TEMPORARY - to investigate tracing. already replaced with kafka! just delete this! */
    @POST
    public Response create(Task task) {
        model.getTasks().computeIfAbsent(task.getForeignReference(), k -> new Vector<>()).add(task.getDescription());
        kafka.publishEvent(TASK_CREATED_EVENT_TOPIC, null, task.getForeignReference());
        return Response.noContent().build();
    }
}
