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
    public static final String GRAPH_CREATE_COMMAND_TOPIC = "graph-create-command";

    @Inject
    ObjectMapper om;

    public Collection<String> getSubscriptionTopics() {
        return asList(LOCATION_CREATE_COMMAND_TOPIC);
    }

    @Override
    public void handleRecord(ConsumerRecord<String, String> r, KafkaAdapter kafkaAdapter) throws Exception {
        Location location = om.readValue(r.value(), Location.class);
        if(LOCATION_CREATE_COMMAND_TOPIC.equals(r.topic())) {
            // create in some DB... No need to send record to UI.
            System.out.println("TODO creating location " + location);

            // now send the record which creates the relationship to the aggregate (claim, partner, whatever...)
            // VERY IMPORTANT, use the aggregate ID as the key, because
            // the location needs to be created afterward the main entity and will use the same ID, to ensure they land on the same partition
            String value = "LOCATION::" + om.writeValueAsString(location);
            kafkaAdapter.publishEvent(GRAPH_CREATE_COMMAND_TOPIC, location.getAggretateId(), value);
        } else {
            System.err.println("received record from unexpected topic " + r.topic() + ": " + r.value());
        }
    }
}
