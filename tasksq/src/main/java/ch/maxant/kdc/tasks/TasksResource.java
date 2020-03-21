package ch.maxant.kdc.tasks;

import io.smallrye.reactive.messaging.annotations.Emitter;
import io.smallrye.reactive.messaging.annotations.Stream;
import io.smallrye.reactive.messaging.kafka.KafkaMessage;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collection;
import java.util.List;
import java.util.Vector;

import static ch.maxant.kdc.tasks.TasksRecordHandler.TASK_CREATED_EVENT_TOPIC;
import static java.util.stream.Collectors.toList;

@Path("/tasks")
public class TasksResource {

    @Inject
    Model model;

    // the following is a little weird, but in order to publish to kafka, we first have to publish internally
    // and then using the method publishToKafka which is annotated with both @Incoming and @Outgoing, publish
    // to kafka. if we used @Stream(TASK_CREATED_EVENT_TOPIC) below, we get an error message when we attempt
    // to send to kafka: "IllegalStateException: Stream not yet connected"
    @Inject
    @Stream("send-task-created-event-to-kafka")
    Emitter<KafkaMessage<String, String>> emitter;

    @Incoming("send-task-created-event-to-kafka")
    @Outgoing(TASK_CREATED_EVENT_TOPIC)
    public KafkaMessage<String, String> publishToKafka(KafkaMessage<String, String> record) {
        return KafkaMessage.of(record.getTopic(), record.getKey(), record.getPayload());
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTasks(@QueryParam("businessKey") String businessKey) {
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
        emitter.send(KafkaMessage.of(TASK_CREATED_EVENT_TOPIC, null, "deleted-all"));
        return Response.ok().build();
    }

    @POST
    @Path("validate")
    @Produces(MediaType.APPLICATION_JSON)
    public Response validate(Task task) {
        // just an example. in reality we'd do some proper validation here!
        if(task.getDescription().equals("invalid")) {
            return Response.ok("{valid: false}").build();
        }
        return Response.ok("{\"valid\": true}").build();
    }
}