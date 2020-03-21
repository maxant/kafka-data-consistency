package ch.maxant.kdc.locations;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.reactive.messaging.kafka.KafkaMessage;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class LocationsRecordHandler {

    public static final String LOCATION_CREATE_COMMAND_TOPIC = "location-create-command";
    public static final String GRAPH_CREATE_COMMAND_TOPIC = "graph-create-command";

    @Inject
    ObjectMapper om;

    @Incoming(LOCATION_CREATE_COMMAND_TOPIC)
    @Outgoing(GRAPH_CREATE_COMMAND_TOPIC)
    public KafkaMessage<String, String> onLocationCreateCommand(KafkaMessage<String, String> r) throws Exception {
        Location location = om.readValue(r.getPayload(), Location.class);
        // create in some DB... No need to send record to UI.
        System.out.println("TODO creating location " + location);

        // now send the record which creates the relationship to the aggregate (claim, partner, whatever...)
        // VERY IMPORTANT, use the aggregate ID as the key, because
        // the location needs to be created afterward the main entity and will use the same ID, to ensure they land on the same partition
        String value = "LOCATION::" + om.writeValueAsString(location);
        return KafkaMessage.of(GRAPH_CREATE_COMMAND_TOPIC, location.getAggretateId(), value);
    }
}
