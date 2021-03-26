package ch.maxant.kdc.webq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class WebRecordHandler {

    public static final String TASK_CREATED_EVENT_TOPIC = "task-created-event";

    public static final String CLAIM_CREATED_EVENT_TOPIC = "claim-created-event";

    @Inject
    WebSocketModel clients;

    @Inject
    ObjectMapper objectMapper;

    @Incoming(TASK_CREATED_EVENT_TOPIC)
    public void onTaskCreated(String record) {
//TODO necessary? consumer.seekToEnd(asList(new TopicPartition(TASK_CREATED_EVENT_TOPIC, 0), new TopicPartition(CLAIM_CREATED_EVENT_TOPIC, 0)));
        sendToAll(TASK_CREATED_EVENT_TOPIC, record);
    }
// TODO these two can be combined  into one, because we can use KafkaMessage as a param and ge tthe topic from that and remove the topic from the config and just configure one
    @Incoming(CLAIM_CREATED_EVENT_TOPIC)
    public void onClaimCreated(String record) {
//TODO necessary? consumer.seekToEnd(asList(new TopicPartition(TASK_CREATED_EVENT_TOPIC, 0), new TopicPartition(CLAIM_CREATED_EVENT_TOPIC, 0)));
        sendToAll(CLAIM_CREATED_EVENT_TOPIC, record);
    }

    private void sendToAll(String topic, String record) {
        // TODO filter => only send data which the client cares about
        Change change = new Change();
        change.setTopic(topic);
        change.setId(record);
        try {
            clients.sendToAll(objectMapper.writeValueAsString(change));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            // TODO handle failure better
        }
    }
}
