package ch.maxant.kdc.partners;

import ch.maxant.kdc.library.JacksonConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class PartnerGenerator {

    public static final Map<Integer, String> COUNTRY_CODES = new HashMap(){{
        put(756, "CH");
        put( 40, "AT");
        put(276, "DE");
        put(826, "UK");
        put(840, "US");
        put(156, "CN");
        put(166, "CC");
        put(214, "DO");
    }};

    public static void main(String[] args) throws JsonProcessingException, ExecutionException, InterruptedException {

        ///////////////////////////////////////////////////
        //
        // randomly generate partner data
        //
        ///////////////////////////////////////////////////

        final Random random = new Random();
        final Properties props = new Properties();
        props.put("bootstrap.servers", "maxant.ch:30001,maxant.ch:30002");
        props.put("acks", "all");
        final KafkaProducer<String, String> producer = new KafkaProducer<>(props, new StringSerializer(), new StringSerializer());
        final ObjectMapper om = JacksonConfig.getMapper();
        while(true) {
            String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            Partner partner = new Partner(
                "Ant-" + now,
                "Kaye",
                new ArrayList<>(COUNTRY_CODES.keySet()).get(random.nextInt(COUNTRY_CODES.size())),
                LocalDate.of(LocalDate.now().getYear() - 18 - random.nextInt(62), 1, 1).plusDays(random.nextInt(365))
            );
            String json = om.writeValueAsString(partner);
            ProducerRecord<String, String> record = new ProducerRecord<>("partner-created-event", null, partner.getId(), json);
            Future<RecordMetadata> f = producer.send(record);
            RecordMetadata recordMetadata = f.get();
            System.out.format("%s - wrote partner to kafka: %s, result: %s\n", now, json, recordMetadata.offset());
            Thread.sleep(100 + random.nextInt(5000));
        }
    }

}
