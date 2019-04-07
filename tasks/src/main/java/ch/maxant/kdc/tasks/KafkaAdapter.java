package ch.maxant.kdc.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.*;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.util.Properties;
import java.util.Vector;

import static java.util.Arrays.asList;
import static javax.ejb.ConcurrencyManagementType.CONTAINER;

@ConcurrencyManagement(CONTAINER)
@Singleton
@LocalBean
@Startup
@Lock(LockType.READ)
public class KafkaAdapter implements Runnable {

    public static final String TASK_CREATE_COMMAND_TOPIC = "task-create-command";

    public static final String TASK_CREATED_EVENT_TOPIC = "task-created-event";

    Producer<String, String> producer;

    Consumer<String, String> consumer;

    @Inject
    Model model;

    @Resource
    ManagedExecutorService executorService;

    @Inject
    ch.maxant.kdc.library.Properties properties;

    @Inject
    ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        Properties props = new Properties();
        props.put("bootstrap.servers", properties.getProperty("kafka.bootstrap.servers"));
        props.put("acks", "all");
        producer = new KafkaProducer<>(props, new StringSerializer(), new StringSerializer());

        props = new Properties();
        props.put("bootstrap.servers", properties.getProperty("kafka.bootstrap.servers"));
        props.put("group.id", "tasks");
        props.put("enable.auto.commit", "true");
        props.put("auto.commit.interval.ms", "1000");
        consumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer());
        consumer.subscribe(asList(TASK_CREATE_COMMAND_TOPIC));

        executorService.submit(this);
    }

    @PreDestroy
    public void shutdown() {
        producer.close();
        consumer.close();
    }

    public void run() {
        try {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
            for(ConsumerRecord<String, String> r : records) {
                try {
                    Task task = objectMapper.readValue(r.value(), Task.class);

                    // create in our DB
                    model.getTasks().computeIfAbsent(task.getForeignReference(), k -> new Vector<>()).add(task.getDescription());

                    // inform UI
                    publishEvent(task.getForeignReference());
                } catch (IOException e) {
                    e.printStackTrace(); // TODO handle better => this causes data loss. rolling back all is also a problem. need to filter this out to a place which admin can investigate
                }
            }
            // TODO is it important to wait for the send futures to complete?
            consumer.commitSync();
        } catch (Exception e) {
            System.err.println("unable to poll: " + e.getMessage());
            e.printStackTrace();
        } finally {
            executorService.submit(this); // instead of blocking a thread with a while loop
        }
    }

    public void publishEvent(String reference) {
        producer.send(new ProducerRecord<>(TASK_CREATED_EVENT_TOPIC, reference));
    }
}
