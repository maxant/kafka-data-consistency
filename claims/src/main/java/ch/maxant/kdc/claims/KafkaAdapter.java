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
import static java.util.Collections.singletonList;
import static javax.ejb.ConcurrencyManagementType.CONTAINER;

@ConcurrencyManagement(CONTAINER)
@Singleton
@LocalBean
@Startup
public class KafkaAdapter implements Runnable {

    public static final String CLAIM_CREATE_DB_COMMAND_TOPIC = "claim-create-db-command";
    public static final String CLAIM_CREATE_SEARCH_COMMAND_TOPIC = "claim-create-search-command";

    public static final String TASK_CREATE_COMMAND_TOPIC = "task-create-command";

    public static final String CLAIM_CREATED_EVENT_TOPIC = "claim-created-event";

    Producer<String, String> producer;

    Consumer<String, String> consumer;

    @Resource
    SessionContext ctx;

    @Resource
    ManagedExecutorService executorService;

    @Inject
    ch.maxant.kdc.library.Properties properties;

    @Inject
    ClaimRepository claimRepository;

    @Inject
    ElasticSearchAdapter elasticSearchAdapter;

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
        consumer.subscribe(asList(CLAIM_CREATE_DB_COMMAND_TOPIC, CLAIM_CREATE_SEARCH_COMMAND_TOPIC));

        executorService.submit(this);
    }

    @PreDestroy
    public void shutdown() {
        producer.close();
        consumer.close();
    }

    // synchronous access because otherwise two threads could interfere with each others transactions.
    // see: https://kafka.apache.org/21/javadoc/index.html?org/apache/kafka/clients/producer/KafkaProducer.html
    // "As is hinted at in the example, there can be only one open transaction per producer."
    // "All messages sent between the beginTransaction() and commitTransaction() calls will be part of a single transaction."
    @Lock(LockType.WRITE)
    public void sendInOneTransaction(List<ProducerRecord<String, String>> records) {
        try {
            producer.beginTransaction();
            records.forEach(r -> producer.send(r));
            // we don't have to wait for all futures to complete. see javadocs:
            // "The transactional producer uses exceptions to communicate error states."
            // "In particular, it is not required to specify callbacks for producer.send() "
            // "or to call .get() on the returned Future: a KafkaException would be thrown "
            // "if any of the producer.send() or transactional calls hit an irrecoverable error during a transaction."
            producer.commitTransaction();
        } catch (KafkaException e) {
            System.err.println("Problem with Kafka");
            e.printStackTrace();
            producer.abortTransaction();
        }
    }

    public void run() {
        try{
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
            for(ConsumerRecord<String, String> r : records) {
                try {
                    Claim claim = objectMapper.readValue(r.value(), Claim.class);

                    if(CLAIM_CREATE_DB_COMMAND_TOPIC.equals(r.topic())) {
                        // create in our DB
                        claimRepository.createClaim(claim);

                        // inform UI. note having to use a transaction and the lock to publish. alternatively, use a difference producer instance.
                        self().sendInOneTransaction(singletonList(new ProducerRecord<>(CLAIM_CREATED_EVENT_TOPIC, claim.getId())));
                    } else if(CLAIM_CREATE_SEARCH_COMMAND_TOPIC.equals(r.topic())) {
                        // create in Elastic. No need to send record to UI.
                        elasticSearchAdapter.createClaim(claim);
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

    /** get a reference to the EJB instance, so that interceptors work, e.g. the lock */
    private KafkaAdapter self() {
        return ctx.getBusinessObject(this.getClass());
    }
}
