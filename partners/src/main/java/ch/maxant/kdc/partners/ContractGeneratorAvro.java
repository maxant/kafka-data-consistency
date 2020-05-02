package ch.maxant.kdc.partners;

import ch.maxant.kdc.contracts.Contract;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class ContractGeneratorAvro {

    public static void main(String[] args) throws ExecutionException, InterruptedException {

        ///////////////////////////////////////////////////
        //
        // playing with avro schema
        // https://avro.apache.org/docs/current/gettingstartedjava.html
        //
        ///////////////////////////////////////////////////

        final Random random = new Random();
        final Properties props = new Properties();
        props.put("bootstrap.servers", "EXTERNAL://maxant.ch:30001,EXTERNAL://maxant.ch:30002");
        props.put("acks", "all");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
        props.put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "http://maxant.ch:30550");
        final KafkaProducer<String, Contract> producer = new KafkaProducer<>(props);

        int contractNumber = 100001;
        while(true) {
            LocalDateTime start = LocalDateTime.now();
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
            ProducerRecord<String, Contract> record = new ProducerRecord<>("contract-created-event", c.getCONTRACTID().toString(), c);
            Future<RecordMetadata> f = producer.send(record);
            RecordMetadata recordMetadata = f.get();
            System.out.format("%s - wrote contract to kafka: %s, result: %s\n", start, c.toString(), recordMetadata.offset());
            Thread.sleep(1000 + random.nextInt(2000));
        }
    }

}
