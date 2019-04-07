package ch.maxant.kdc.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.LocalBean;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.inject.Inject;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;

import static java.util.Arrays.asList;
import static javax.ejb.ConcurrencyManagementType.CONTAINER;

@ConcurrencyManagement(CONTAINER)
@Singleton
@LocalBean
@Startup
public class KafkaAdapter implements Runnable {

    public static final String TASK_CREATED_EVENT_TOPIC = "task-created-event";

    public static final String CLAIM_CREATED_EVENT_TOPIC = "claim-created-event";

    Consumer<String, String> consumer;

    @Resource
    ManagedExecutorService executorService;

    @Inject
    ch.maxant.kdc.library.Properties properties;

    @Inject
    WebSocketModel clients;

    @Inject
    ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        Properties props = new Properties();
        props.put("bootstrap.servers", properties.getProperty("kafka.bootstrap.servers"));
        props.put("group.id", "web-" + UUID.randomUUID()); // use UUID, so that every instance gets data, otherwise clients will not necessarily get their data
        props.put("enable.auto.commit", "true");
        props.put("auto.commit.interval.ms", "1000");
        System.out.println("asdf");
        consumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer());
        consumer.subscribe(asList(TASK_CREATED_EVENT_TOPIC, CLAIM_CREATED_EVENT_TOPIC));
//TODO fixme consumer.seekToEnd(asList(new TopicPartition(TASK_CREATED_EVENT_TOPIC, 0), new TopicPartition(CLAIM_CREATED_EVENT_TOPIC, 0)));

        executorService.submit(this);
    }

    @PreDestroy
    public void shutdown() {
        consumer.close();
    }

    public void run() {
        try {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
            for(ConsumerRecord<String, String> record : records) {
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
            consumer.commitSync();
        } catch (Exception e) {
            System.err.println("unable to poll: " + e.getMessage());
            e.printStackTrace();
        } finally {
            executorService.submit(this); // instead of blocking a thread with a while loop
        }
    }
}
