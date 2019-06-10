package ch.maxant.kdc.claims;

import ch.maxant.kdc.library.KafkaAdapter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

import static ch.maxant.kdc.claims.ClaimsRecordHandler.*;
import static java.util.Arrays.asList;

@Path("claims")
@ApplicationScoped
public class ClaimResource {

    @Inject
    KafkaAdapter kafka;

    @Inject
    ObjectMapper om;

    @Inject
    ClaimRepository claimRepository;

    @Inject
    TaskService taskService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response get() {
        return Response.ok(claimRepository.getClaims()).build();
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(Claim claim) throws JsonProcessingException {

        // (1) send data to claims database
        ProducerRecord<String, String> claimDbRecord = new ProducerRecord<>(CLAIM_CREATE_DB_COMMAND_TOPIC, null, null, om.writeValueAsString(claim));

        // (2) send data to elastic search, our "search system"
        ProducerRecord<String, String> claimSearchRecord = new ProducerRecord<>(CLAIM_CREATE_SEARCH_COMMAND_TOPIC, null, null, om.writeValueAsString(claim));

        // (3) create a task, validate it, and send it to the tasks component for persisting it there
        Task task = new Task(claim.getId(), "call customer " + claim.getPartnerId());
        // an example of online sync validation, if that is required. note that it reduces
        // robustness and availabiliy of the claims component!
        taskService.validate(task);
        ProducerRecord<String, String> createTaskCommand = new ProducerRecord<>(TASK_CREATE_COMMAND_TOPIC, null, null, om.writeValueAsString(task));

        // collect the records in preparation for sending them en bloc
        List<ProducerRecord<String, String>> records = new ArrayList<>(asList(claimDbRecord, claimSearchRecord, createTaskCommand));

        // handle the location if present
        if(claim.getLocation() != null) {
            claim.getLocation().setAggretateId(claim.getId()); // enrich the record because the UI doesn't know this field
            claim.getLocation().setType(Location.LocationType.CLAIM_LOCATION); // ditto
            // (4) send the location data to the location component for persisting it there
            ProducerRecord<String, String> createLocationCommand = new ProducerRecord<>(LOCATION_CREATE_COMMAND_TOPIC, null, null, om.writeValueAsString(claim.getLocation()));
            records.add(createLocationCommand);
        }

        // (5) send data to neo4j, our "graph analytics system" - VERY IMPORTANT, use the claim ID as the key, because
        // the location needs to be created afterwards and will use the same ID, to ensure they land on the same partition
        String value = "CLAIM::" + om.writeValueAsString(claim);
        ProducerRecord<String, String> claimRelationshipsRecord = new ProducerRecord<>(GRAPH_CREATE_COMMAND_TOPIC, null, claim.getId(), value);
        records.add(claimRelationshipsRecord);

        // (6) now send all those "commands" in one tx
        kafka.sendInOneTransaction(records);

        return Response.accepted().build();
    }

    /** THIS METHOD IS JUST FOR DEMO PURPOSES - DELETE FOR PRODUCTION */
    @DELETE
    public Response delete() {
        claimRepository.delete();
        kafka.sendInOneTransaction(asList(new ProducerRecord<>(CLAIM_CREATED_EVENT_TOPIC, null, null, "deleted-all")));
        return Response.ok().build();
    }

}
