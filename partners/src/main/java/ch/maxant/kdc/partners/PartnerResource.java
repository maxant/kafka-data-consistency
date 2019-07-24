package ch.maxant.kdc.partners;

import ch.maxant.kdc.library.JacksonConfig;
import ch.maxant.kdc.library.KafkaAdapter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static java.util.Collections.singletonList;

@Path("partners")
@ApplicationScoped
public class PartnerResource {

    @Inject
    KafkaAdapter kafka;

    @Inject
    ObjectMapper om;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(Partner partner) throws JsonProcessingException {

        ProducerRecord<String, String> record = new ProducerRecord<>("ksql-test-cud-partners", null, partner.getId(), om.writeValueAsString(partner));
        kafka.sendInOneTransaction(singletonList(record));

        return Response.accepted().build();
    }

    // TODO delete this - its just for testing purposes
    public static void main(String[] args) throws JsonProcessingException, ExecutionException, InterruptedException {
        final Random random = new Random();
        final Properties props = new Properties();
        props.put("bootstrap.servers", "maxant.ch:30001,maxant.ch:30002");
        props.put("acks", "all");
        final KafkaProducer producer = new KafkaProducer<>(props, new StringSerializer(), new StringSerializer());
        final ObjectMapper om = JacksonConfig.getMapper();
        while(true) {
            String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            Partner partner = new Partner(
                "Ant-" + now,
                "Kaye",
                754 + random.nextInt(3),
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
