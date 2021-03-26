package ch.maxant.kdc.partners;

import ch.maxant.kdc.contracts.Contract;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static java.util.Collections.singleton;

public class ContractReaderAvro {

    public static final String TOPIC = "contract-created-event";

    public static void main(String[] args) throws ExecutionException, InterruptedException {

        ///////////////////////////////////////////////////
        //
        // playing with avro schema
        // https://avro.apache.org/docs/current/gettingstartedjava.html
        //
        ///////////////////////////////////////////////////

        final Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "maxant.ch:30001,maxant.ch:30002");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "aGroup");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        // necessary if auto commit is enabled: props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName());
        //Use Specific Record or else you get Avro GenericRecord.
        props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, "true");
        props.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "http://maxant.ch:30550");

        // useful if you have data in a topic and the first time a program runs, it needs to do an initial load of all that data
        // props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        KafkaConsumer<String, Contract> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(singleton(TOPIC));

        // see https://stackoverflow.com/a/54383979
        long start = System.currentTimeMillis();
        while(consumer.assignment().isEmpty()) {
            consumer.poll(Duration.ofMillis(0)); // in order to have an assignement at all
        }
        System.out.printf("seeking to beginning after %sms\r\n", (System.currentTimeMillis() - start));
        consumer.seekToBeginning(consumer.assignment()); // will be reset on next poll as this is lazy

        while (true) {
            // avro: if the contract class specified in the record is unknown, e.g. we havent generated it,
            // then we get this error during polling:
            //   Exception in thread "main" org.apache.kafka.common.errors.SerializationException: Error deserializing key/value for partition contract-created-event-0 at offset 340. If needed, please seek past the record to continue consumption.
            //   Caused by: org.apache.kafka.common.errors.SerializationException: Could not find class ch.maxant.kdc.contracts.Contract specified in writer's schema whilst finding reader's schema for a SpecificRecord.
            //
            // if a required field is missing in the schema, we get this error:
            //   Exception in thread "main" org.apache.kafka.common.errors.SerializationException: Error deserializing key/value for partition contract-created-event-0 at offset 0. If needed, please seek past the record to continue consumption.
            //   Caused by: org.apache.kafka.common.errors.SerializationException: Error deserializing Avro message for id 1
            //   Caused by: org.apache.avro.AvroTypeException: Found ch.maxant.kdc.contracts.Contract, expecting ch.maxant.kdc.contracts.Contract, missing required field DOESNT_EXIST
            final ConsumerRecords<String, Contract> records = consumer.poll(Duration.ofMillis(Integer.MAX_VALUE));
            System.out.println("received " + records.count() + " records");
            for (ConsumerRecord<String, Contract> record : records) {
                System.out.println("got a record with key: " + record.key() + ", begin: " + record.value().getBEGIN() + ", end_ts: " + record.value().getENDTS() + ", offset: " + record.offset());
            }
            System.out.println("committing");
            consumer.commitSync();
        }
    }

}
