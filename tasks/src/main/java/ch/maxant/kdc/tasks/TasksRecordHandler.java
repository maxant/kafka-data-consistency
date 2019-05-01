package ch.maxant.kdc.tasks;

import ch.maxant.kdc.library.KafkaAdapter;
import ch.maxant.kdc.library.RecordHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Collection;
import java.util.Vector;

import static java.util.Arrays.asList;

@ApplicationScoped
public class TasksRecordHandler implements RecordHandler {

    public static final String TASK_CREATE_COMMAND_TOPIC = "task-create-command";

    public static final String TASK_CREATED_EVENT_TOPIC = "task-created-event";

    @Inject
    ObjectMapper objectMapper;

    @Inject
    Model model;

    public Collection<String> getSubscriptionTopics() {
        return asList(TASK_CREATE_COMMAND_TOPIC);
    }

    @Override
    public void handleRecord(ConsumerRecord<String, String> r, KafkaAdapter kafkaAdapter) throws Exception {
        Task task = objectMapper.readValue(r.value(), Task.class);

        // create in our DB
        model.getTasks().computeIfAbsent(task.getForeignReference(), k -> new Vector<>()).add(task.getDescription());

        // inform UI
        kafkaAdapter.publishEvent(TASK_CREATED_EVENT_TOPIC, null, task.getForeignReference());
    }

    @Override
    public boolean useTransactions() {
        return false;
    }
}
