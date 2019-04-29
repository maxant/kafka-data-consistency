package ch.maxant.kdc.claims;

import ch.maxant.kdc.library.KafkaAdapter;
import ch.maxant.kdc.library.telemetry.Measured;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

import static ch.maxant.kdc.claims.ClaimsRecordHandler.*;
import static java.util.Arrays.asList;

@Path("claims")
@ApplicationScoped
@Measured
public class ClaimResource {

    @Inject
    KafkaAdapter kafka;

    @Inject
    ObjectMapper om;

    @Inject
    ClaimRepository claimRepository;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response get() {
        return Response.ok(claimRepository.getClaims()).build();
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(Claim claim) throws JsonProcessingException {

        ProducerRecord<String, String> claimDbRecord = new ProducerRecord<>(CLAIM_CREATE_DB_COMMAND_TOPIC, null, null, om.writeValueAsString(claim));
        ProducerRecord<String, String> claimSearchRecord = new ProducerRecord<>(CLAIM_CREATE_SEARCH_COMMAND_TOPIC, null, null, om.writeValueAsString(claim));
        ProducerRecord<String, String> claimRelationshipRecord = new ProducerRecord<>(CLAIM_CREATE_RELATIONSHIP_COMMAND_TOPIC, null, null, om.writeValueAsString(claim));
        ProducerRecord<String, String> createTaskCommand = new ProducerRecord<>(TASK_CREATE_COMMAND_TOPIC, null, null, om.writeValueAsString(new Task(claim.getId(), "call customer " + claim.getPartnerId())));

        claim.getLocation().setAggretateId(claim.getId());
        claim.getLocation().setType(Location.LocationType.CLAIM_LOCATION);
        ProducerRecord<String, String> createLocationCommand = new ProducerRecord<>(LOCATION_CREATE_COMMAND_TOPIC, null, null, om.writeValueAsString(claim.getLocation()));

        List<ProducerRecord<String, String>> records = asList(claimDbRecord, claimSearchRecord, claimRelationshipRecord, createTaskCommand, createLocationCommand);

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
