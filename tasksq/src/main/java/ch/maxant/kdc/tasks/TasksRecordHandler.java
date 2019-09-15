package ch.maxant.kdc.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.reactive.messaging.kafka.KafkaMessage;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Vector;

@ApplicationScoped
public class TasksRecordHandler {

    public static final String TASK_CREATE_COMMAND_TOPIC = "task-create-command";

    public static final String TASK_CREATED_EVENT_TOPIC = "task-created-event";

    @Inject
    ObjectMapper objectMapper;

    @Inject
    Model model;

    @Incoming(TASK_CREATE_COMMAND_TOPIC)
    @Outgoing(TASK_CREATED_EVENT_TOPIC)
    public KafkaMessage<String, String> onTaskCreateCommand(KafkaMessage<String, String> record) throws Exception {
        Task task = objectMapper.readValue(record.getPayload(), Task.class);

        // create in our DB
        model.getTasks().computeIfAbsent(task.getForeignReference(), k -> new Vector<>()).add(task.getDescription());

        // inform UI
        return KafkaMessage.of(TASK_CREATED_EVENT_TOPIC, null, task.getForeignReference());
    }
}
