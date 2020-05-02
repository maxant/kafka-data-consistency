package ch.maxant.kdc.partners;

import ch.maxant.kdc.contracts.Contract;
import ch.maxant.kdc.contracts.ContractLite;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static java.util.Collections.singleton;

public class ContractGeneratorAvro {

    public static final String TOPIC = "contract-created-event";

    public static void main(String[] args) throws ExecutionException, InterruptedException {

        ///////////////////////////////////////////////////
        //
        // playing with avro schema
        // https://avro.apache.org/docs/current/gettingstartedjava.html
        //
        ///////////////////////////////////////////////////

        final Random random = new Random();

        {
            final Properties props = new Properties();
            props.put("bootstrap.servers", "maxant.ch:30001,maxant.ch:30002");
            props.put("group.id", "aGroup");
            props.put("enable.auto.commit", "true");
            props.put("auto.commit.interval.ms", "1000");

            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName());
            //Use Specific Record or else you get Avro GenericRecord.
            props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, "true");
            props.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "http://maxant.ch:30550");

            KafkaConsumer<String, ContractLite> consumer = new KafkaConsumer<>(props);
            consumer.subscribe(singleton(TOPIC));
            new Thread(() -> {
                while (true) {
                    final ConsumerRecords<String, ContractLite> records = consumer.poll(Duration.ofMillis(100));
                    for (ConsumerRecord<String, ContractLite> record : records) {
                        System.out.println("got a record: " + record.key() + ", " + record.value().getCONTRACTID());
                    }
                }
            }).start();
        }

        {
            final Properties props = new Properties();
            props.put("bootstrap.servers", "maxant.ch:30001,maxant.ch:30002");
            props.put("acks", "all");
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
            props.put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "http://maxant.ch:30550");
            final KafkaProducer<String, Contract> producer = new KafkaProducer<>(props);

            int contractNumber = 100001;
            while(true) {
                LocalDateTime start = LocalDate.now().atStartOfDay();
                LocalDateTime end = start.plusYears(2).minusNanos(1_000_000);
                Contract c = new Contract(
                        UUID.randomUUID().toString(),
                        String.valueOf(random.nextInt(4000)),
                        start.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        end.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        start.toInstant(ZoneOffset.UTC),
                        end.toInstant(ZoneOffset.UTC),
                        contractNumber++,
                        "MAA");
                ProducerRecord<String, Contract> record = new ProducerRecord<>(TOPIC, c.getCONTRACTID().toString(), c);
                Future<RecordMetadata> f = producer.send(record);
                RecordMetadata recordMetadata = f.get();
                System.out.format("%s - wrote contract to kafka: %s, result: %s\n", start, c.toString(), recordMetadata.offset());
                Thread.sleep(1000 + random.nextInt(2000));
            }
        }
    }

}
