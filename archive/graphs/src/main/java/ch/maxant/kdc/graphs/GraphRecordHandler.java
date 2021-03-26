package ch.maxant.kdc.graphs;

import ch.maxant.kdc.library.KafkaAdapter;
import ch.maxant.kdc.library.RecordHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Collection;

import static java.util.Arrays.asList;

@ApplicationScoped
public class GraphRecordHandler implements RecordHandler {

    public static final String GRAPH_CREATE_COMMAND_TOPIC = "graph-create-command";

    @Inject
    ObjectMapper objectMapper;

    @Inject
    Neo4JAdapter neo4JAdapter;

    public Collection<String> getSubscriptionTopics() {
        return asList(GRAPH_CREATE_COMMAND_TOPIC);
    }

    @Override
    public void handleRecord(ConsumerRecord<String, String> r, KafkaAdapter kafkaAdapter) throws Exception {
        if(GRAPH_CREATE_COMMAND_TOPIC.equals(r.topic())) {
            if(r.value().startsWith("CLAIM::")) {
                // create in Neo4J. No need to send record to UI.
                Claim claim = objectMapper.readValue(r.value().substring(7), Claim.class);
                neo4JAdapter.createClaim(claim);
            } else if (r.value().startsWith("LOCATION::")) {
                Location location = objectMapper.readValue(r.value().substring(10), Location.class);
                neo4JAdapter.createLocation(location);
            } else {
                System.err.println("received unexpected record " + r.key() + ": " + r.value());
            }
        } else {
            System.err.println("received record from unexpected topic " + r.topic() + ": " + r.value());
        }
    }

    @Override
    public boolean useTransactions() {
        return false;
    }
}
