package ch.maxant.kdc.locations;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.*;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static javax.ejb.ConcurrencyManagementType.CONTAINER;

@ConcurrencyManagement(CONTAINER)
@Singleton
@LocalBean
@Startup
public class KafkaAdapter implements Runnable {

    public static final String LOCATION_CREATE_COMMAND_TOPIC = "location-create-command";

    Consumer<String, String> consumer;

    @Resource
    ManagedExecutorService executorService;

    @Inject
    ch.maxant.kdc.library.Properties properties;

    @Inject
    Neo4JAdapter neo4JAdapter;

    @Inject
    ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        Properties props = new Properties();
        props.put("bootstrap.servers", properties.getProperty("kafka.bootstrap.servers"));
        props.put("group.id", "claims");
        props.put("enable.auto.commit", "true");
        props.put("auto.commit.interval.ms", "1000");
        consumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer());
        consumer.subscribe(asList(LOCATION_CREATE_COMMAND_TOPIC));

        executorService.submit(this);
    }

    @PreDestroy
    public void shutdown() {
        producer.close();
        consumer.close();
    }

    public void run() {
        try{
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
            for(ConsumerRecord<String, String> r : records) {
                try {
                    Claim claim = objectMapper.readValue(r.value(), Claim.class);

                    if(LOCATION_CREATE_COMMAND_TOPIC.equals(r.topic())) {
                        // create in Neo4J. No need to send record to UI.
                        neo4JAdapter.createClaim(claim);
                    } else {
                        System.err.println("received record from unexpected topic " + r.topic() + ": " + r.value());
                    }
                } catch (Exception e) {
                    // TODO handle better => this causes data loss.
                    //  rolling back all is also a problem, as successful ones will be replayed.
                    //  need to filter this out to a place which admin can investigate
                    e.printStackTrace();
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
