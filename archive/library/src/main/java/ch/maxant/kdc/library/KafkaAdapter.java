package ch.maxant.kdc.library;

import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Scope;
import co.elastic.apm.api.Span;
import co.elastic.apm.api.Transaction;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.*;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.inject.Inject;
import java.time.Duration;
import java.util.Properties;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.ejb.ConcurrencyManagementType.CONTAINER;

@ConcurrencyManagement(CONTAINER)
@Singleton
@LocalBean
@Startup
public class KafkaAdapter implements Runnable {

    Producer<String, String> producer;

    Consumer<String, String> consumer;

    @Resource
    SessionContext ctx;

    @Resource
    ManagedExecutorService executorService;

    @Inject
    ch.maxant.kdc.library.Properties properties;

    @Inject
    RecordHandler recordHandler;

    @PostConstruct
    public void init() {
        Properties props = new Properties();
        props.put("bootstrap.servers", properties.getProperty("kafka.bootstrap.servers"));
        props.put("acks", "all");
        if(recordHandler.useTransactions()) {
            props.put("transactional.id", recordHandler.getComponentName() + "-transactional-id-" + UUID.randomUUID()); // unique coz each producer needs a unique id
        }
        producer = new KafkaProducer<>(props, new StringSerializer(), new StringSerializer());
        if(recordHandler.useTransactions()) {
            producer.initTransactions();
        }

        props = new Properties();
        props.put("bootstrap.servers", properties.getProperty("kafka.bootstrap.servers"));
        props.put("group.id", recordHandler.getComponentName());
        props.put("enable.auto.commit", "true");
        props.put("auto.commit.interval.ms", "1000");
        consumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer());
        consumer.subscribe(recordHandler.getSubscriptionTopics());

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

            Transaction tracingTx = ElasticApm.currentTransaction();

            records.forEach(record -> publishRecordWithTracing(tracingTx, record));

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

    @Lock(LockType.READ) // sending without a transaction can be done by multiple threads at a time
    public void publishEvent(String topic, String key, String value) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
        publishRecordWithTracing(ElasticApm.currentTransaction(), record);
    }

    public void run() {
        try{
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
            for(ConsumerRecord<String, String> r : records) {

                r.headers().forEach(h -> System.out.println("header in incoming record: " + h.key() + "/" + new String(h.value(), UTF_8)));

                Transaction transaction = startTracingTx(r);
                transaction.setName(r.topic());
                transaction.setType(Transaction.TYPE_REQUEST);

                try (final Scope scope = transaction.activate()) {
                    recordHandler.handleRecord(r, self()); // TODO doing it this way means we are effectively serial! a later version should handle records in parallel
                } catch (Exception e) {
                    transaction.captureException(e);

                    // TODO handle better => this causes data loss.
                    //  rolling back all is also a problem, as successful ones will be replayed.
                    //  need to filter this out to a place which admin can investigate
                    e.printStackTrace();
                } finally {
                    transaction.end();
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

    private void publishRecordWithTracing(Transaction transaction, ProducerRecord<String, String> record) {
        // https://discuss.elastic.co/t/java-agent-how-to-manually-instrument-an-rpc-framework-which-is-not-already-supported/172730
        // https://discuss.elastic.co/t/does-elastic-apm-support-spring-cloud-stream/173620/3
        // https://discuss.elastic.co/t/java-agent-custom-span-in-multithread-context/178608/10
        // https://discuss.elastic.co/t/apm-problem-with-java-standalone/175214/3

        // https://www.elastic.co/guide/en/apm/agent/java/current/public-api.html#api-transaction-inject-trace-headers
        Span span = transaction
                .startSpan("external", "kafka", record.topic())
                .setName(record.topic());
        try (final Scope scope = transaction.activate()) {
            span.injectTraceHeaders((name, value2) -> {
                System.out.println("publishEvent injecting: name=" + name + ", value=" + value2);
                record.headers().add(name, value2.getBytes(UTF_8));
            });
            producer.send(record);
        } catch (Exception e) {
            span.captureException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    private Transaction startTracingTx(ConsumerRecord<String, String> r) {
        // https://www.elastic.co/guide/en/apm/agent/java/current/public-api.html#api-start-transaction-with-remote-parent-headers
        return ElasticApm.startTransactionWithRemoteParent(key -> {
                        System.out.println("reading tracing header for key " + key);
                        return new String(r.headers().headers(key).iterator().next().value(), UTF_8);
                    }, headerName -> {
                        System.out.println("reading tracing headers for key " + headerName);
                        Iterator<Header> iterator = r.headers().headers(headerName).iterator();
                        List<String> out = new ArrayList<>();
                        while(iterator.hasNext()) {
                            out.add(new String(iterator.next().value(), UTF_8));
                        }
                        return out;
                    });
    }
}
