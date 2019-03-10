package ch.maxant.kdc.claims;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static java.util.Arrays.asList;

@Path("create")
@ApplicationScoped
public class ClaimResource {

    @Inject
    KafkaAdapter kafka;

    @Inject
    ObjectMapper om;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(Claim claim) throws JsonProcessingException {

        ProducerRecord<String, String> claimRecord = new ProducerRecord<>("claim-create", null, null, om.writeValueAsString(claim));
        ProducerRecord<String, String> createTaskCommand = new ProducerRecord<>("task-create", null, null, om.writeValueAsString(new Task(claim.getId(), "call customer " + claim.getCustomerId())));
        kafka.sendInOneTransaction(asList(claimRecord, createTaskCommand));

        return Response.accepted().build();
    }

}
