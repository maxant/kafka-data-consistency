package ch.maxant.kdc.claims;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.serialization.StringSerializer;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.inject.Inject;
import java.util.*;

import static javax.ejb.ConcurrencyManagementType.CONTAINER;

@ConcurrencyManagement(CONTAINER)
@Singleton
public class KafkaAdapter {

    Producer<String, String> producer;

    @Inject
    ch.maxant.kdc.library.Properties properties;

    @PostConstruct
    public void init() {
        Properties props = new Properties();
        props.put("bootstrap.servers", properties.getProperty("kafka.bootstrap.servers"));
        props.put("acks", "all");
        props.put("transactional.id", "claims-transactional-id-" + UUID.randomUUID()); // unique coz each producer needs a unique id
        producer = new KafkaProducer<>(props, new StringSerializer(), new StringSerializer());
        producer.initTransactions();
    }

    @PreDestroy
    public void shutdown() {
        producer.close();
    }

    @Lock(LockType.WRITE) // synchronous access because otherwise two threads could interfere with each others transactions
    public void sendInOneTransaction(List<ProducerRecord<String, String>> records) {
        try {
            producer.beginTransaction();
            records.forEach(r -> producer.send(r));
            producer.commitTransaction();
        } catch (KafkaException e) {
            System.err.println("kafka had a problem");
            e.printStackTrace();
            producer.abortTransaction();
        }
    }
}
