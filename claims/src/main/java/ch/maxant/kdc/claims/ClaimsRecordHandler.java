package ch.maxant.kdc.claims;

import ch.maxant.kdc.library.KafkaAdapter;
import ch.maxant.kdc.library.RecordHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Collection;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

@ApplicationScoped
public class ClaimsRecordHandler implements RecordHandler {

    public static final String CLAIM_CREATE_DB_COMMAND_TOPIC = "claim-create-db-command";
    public static final String CLAIM_CREATE_SEARCH_COMMAND_TOPIC = "claim-create-search-command";
    public static final String CLAIM_CREATE_RELATIONSHIP_COMMAND_TOPIC = "claim-create-relationship-command";

    public static final String TASK_CREATE_COMMAND_TOPIC = "task-create-command";

    public static final String LOCATION_CREATE_COMMAND_TOPIC = "location-create-command";

    public static final String CLAIM_CREATED_EVENT_TOPIC = "claim-created-event";

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ClaimRepository claimRepository;

    @Inject
    ElasticSearchAdapter elasticSearchAdapter;

    @Inject
    Neo4JAdapter neo4JAdapter;

    public Collection<String> getSubscriptionTopics() {
        return asList(CLAIM_CREATE_DB_COMMAND_TOPIC, CLAIM_CREATE_SEARCH_COMMAND_TOPIC, CLAIM_CREATE_RELATIONSHIP_COMMAND_TOPIC);
    }

    @Override
    public void handleRecord(ConsumerRecord<String, String> r, KafkaAdapter kafkaAdapter) throws Exception {
        Claim claim = objectMapper.readValue(r.value(), Claim.class);
        if(CLAIM_CREATE_DB_COMMAND_TOPIC.equals(r.topic())) {
            // create in our DB
            claimRepository.createClaim(claim);

            // inform UI. note having to use a transaction and the lock to publish. alternatively, use a difference producer instance.
            kafkaAdapter.sendInOneTransaction(singletonList(new ProducerRecord<>(CLAIM_CREATED_EVENT_TOPIC, claim.getId())));
        } else if(CLAIM_CREATE_SEARCH_COMMAND_TOPIC.equals(r.topic())) {
            // create in Elastic. No need to send record to UI.
            elasticSearchAdapter.createClaim(claim);
        } else if(CLAIM_CREATE_RELATIONSHIP_COMMAND_TOPIC.equals(r.topic())) {
            // create in Neo4J. No need to send record to UI.
            neo4JAdapter.createClaim(claim);
        } else {
            System.err.println("received record from unexpected topic " + r.topic() + ": " + r.value());
        }
    }

    @Override
    public String getComponentName() {
        return "claims";
    }

    @Override
    public boolean useTransactions() {
        return true;
    }
}
