package ch.maxant.kdc.partners;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Properties;

import static ch.maxant.kdc.partners.ThroughputTestStream.fromJson;
import static org.apache.kafka.clients.consumer.ConsumerConfig.*;

public class ThroughputResult {

    public static void main(String[] args) {

        ///////////////////////////////////////////////////
        //
        // listen to data on output topic
        //
        ///////////////////////////////////////////////////

        final Properties props = new Properties();
        props.put(BOOTSTRAP_SERVERS_CONFIG, "maxant.ch:30001,maxant.ch:30002");
        props.put(CLIENT_ID_CONFIG, "throughput-test-result-consumer");
        props.put(GROUP_ID_CONFIG, "throughput-test-result-group-id");
        final KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer());
        consumer.subscribe(Collections.singletonList("throughput-test-aggregated"));
        final ObjectMapper om = new ObjectMapper();

        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.of(1, ChronoUnit.SECONDS));
            records.iterator().forEachRemaining(r -> {
                ThroughputAggregateRecord record = fromJson(om, r.value(), ThroughputAggregateRecord.class);
                if(record.getNumRecordsProcessed() != 10) {
                    System.out.printf("%s %s\r\n", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), r);
                } else {
                    System.out.printf("%s aggregate was ok\r\n", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                }
            });
        }
    }

}
