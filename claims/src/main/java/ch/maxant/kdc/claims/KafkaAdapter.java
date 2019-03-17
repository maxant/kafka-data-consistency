package ch.maxant.kdc.claims;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
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
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static java.util.Arrays.asList;
import static javax.ejb.ConcurrencyManagementType.CONTAINER;

@ConcurrencyManagement(CONTAINER)
@Singleton
@LocalBean
@Startup
public class KafkaAdapter implements Runnable {

    public static final String CLAIM_CREATE_COMMAND_TOPIC = "claim-create-command";

    public static final String TASK_CREATE_COMMAND_TOPIC = "task-create-command";

    public static final String CLAIM_CREATED_EVENT_TOPIC = "claim-created-event";

    Producer<String, String> producer;

    Consumer<String, String> consumer;

    @Resource
    ManagedExecutorService executorService;

    @Inject
    ch.maxant.kdc.library.Properties properties;

    @Inject
    ClaimRepository claimRepository;

    @Inject
    ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        Properties props = new Properties();
        props.put("bootstrap.servers", properties.getProperty("kafka.bootstrap.servers"));
        props.put("acks", "all");
        props.put("transactional.id", "claims-transactional-id-" + UUID.randomUUID()); // unique coz each producer needs a unique id
        producer = new KafkaProducer<>(props, new StringSerializer(), new StringSerializer());
        producer.initTransactions();

        props = new Properties();
        props.put("bootstrap.servers", properties.getProperty("kafka.bootstrap.servers"));
        props.put("group.id", "claims");
        props.put("enable.auto.commit", "true");
        props.put("auto.commit.interval.ms", "1000");
        consumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer());
        consumer.subscribe(asList(CLAIM_CREATE_COMMAND_TOPIC));

        executorService.submit(this);
    }

    @PreDestroy
    public void shutdown() {
        producer.close();
        consumer.close();
    }

    @Lock(LockType.WRITE) // synchronous access because otherwise two threads could interfere with each others transactions
    public void sendInOneTransaction(List<ProducerRecord<String, String>> records) {
        try {
            producer.beginTransaction();
            records.forEach(r -> producer.send(r));
            // i assume we don't have to wait for all futures to complete before committing, because the commit is also sent to kafka
            producer.commitTransaction();
        } catch (KafkaException e) {
            System.err.println("Problem with Kafka");
            e.printStackTrace();
            producer.abortTransaction();
        }
    }

    public void run() {
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
        for(ConsumerRecord<String, String> r : records) {
            try {
                Claim claim = objectMapper.readValue(r.value(), Claim.class);

                // create in our DB
                claimRepository.createClaim(claim);

                // inform UI
                producer.send(new ProducerRecord<>(CLAIM_CREATED_EVENT_TOPIC, claim.getId()));
            } catch (IOException e) {
                e.printStackTrace(); // TODO handle better => this causes data loss. rolling back all is also a problem. need to filter this out to a place which admin can investigate
            }
        }
        consumer.commitSync();
        executorService.submit(this); // instead of blocking a thread with a while loop
    }
}
