package ch.maxant.kdc.web;

import ch.maxant.kdc.library.KafkaAdapter;
import ch.maxant.kdc.library.RecordHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Collection;

import static java.util.Arrays.asList;

@ApplicationScoped
public class WebRecordHandler implements RecordHandler {

    public static final String TASK_CREATED_EVENT_TOPIC = "task-created-event";

    public static final String CLAIM_CREATED_EVENT_TOPIC = "claim-created-event";

    @Inject
    WebSocketModel clients;

    @Inject
    ObjectMapper objectMapper;

    public Collection<String> getSubscriptionTopics() {
        return asList(TASK_CREATED_EVENT_TOPIC, CLAIM_CREATED_EVENT_TOPIC);
//TODO fixme consumer.seekToEnd(asList(new TopicPartition(TASK_CREATED_EVENT_TOPIC, 0), new TopicPartition(CLAIM_CREATED_EVENT_TOPIC, 0)));
    }

    @Override
    public void handleRecord(ConsumerRecord<String, String> record, KafkaAdapter kafkaAdapter) throws Exception {
        // TODO filter => only send data which the client cares about
        Change change = new Change();
        change.setTopic(record.topic());
        change.setId(record.value());
        try {
            clients.sendToAll(objectMapper.writeValueAsString(change));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            // TODO handle failure better
        }
    }
}
