package ch.maxant.kdc.claims;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static ch.maxant.kdc.claims.KafkaAdapter.CLAIM_CREATE_COMMAND_TOPIC;
import static ch.maxant.kdc.claims.KafkaAdapter.TASK_CREATE_COMMAND_TOPIC;
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

    @POST // using post because of CORS+json
    @Path("read")
    @Produces(MediaType.APPLICATION_JSON)
    public Response get() {
        return Response.ok(claimRepository.getClaims()).build();
    }

    @POST
    @Path("create")
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(Claim claim) throws JsonProcessingException {
        ProducerRecord<String, String> claimRecord = new ProducerRecord<>(CLAIM_CREATE_COMMAND_TOPIC, null, null, om.writeValueAsString(claim));
        ProducerRecord<String, String> createTaskCommand = new ProducerRecord<>(TASK_CREATE_COMMAND_TOPIC, null, null, om.writeValueAsString(new Task(claim.getId(), "call customer " + claim.getCustomerId())));
        kafka.sendInOneTransaction(asList(claimRecord, createTaskCommand));

        System.out.println("Created ");
        return Response.accepted().header("Access-Control-Allow-Origin", "*").build();
    }

}
