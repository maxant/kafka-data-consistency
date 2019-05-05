package ch.maxant.kdc.locations;

import ch.maxant.kdc.library.KafkaAdapter;
import ch.maxant.kdc.library.RecordHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Collection;

import static java.util.Arrays.asList;

@ApplicationScoped
public class LocationsRecordHandler implements RecordHandler {

    public static final String LOCATION_CREATE_COMMAND_TOPIC = "location-create-command";

    @Inject
    ObjectMapper objectMapper;

    @Inject
    Neo4JAdapter neo4JAdapter;

    public Collection<String> getSubscriptionTopics() {
        return asList(LOCATION_CREATE_COMMAND_TOPIC);
    }

    @Override
    public void handleRecord(ConsumerRecord<String, String> r, KafkaAdapter kafkaAdapter) throws Exception {
        Location location = objectMapper.readValue(r.value(), Location.class);
        if(LOCATION_CREATE_COMMAND_TOPIC.equals(r.topic())) {
            // create in Neo4J. No need to send record to UI.
            neo4JAdapter.createLocation(location);
        } else {
            System.err.println("received record from unexpected topic " + r.topic() + ": " + r.value());
        }
    }
}
