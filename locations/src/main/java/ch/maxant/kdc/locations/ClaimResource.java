package ch.maxant.kdc.claims;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static ch.maxant.kdc.claims.KafkaAdapter.*;
import static java.util.Arrays.asList;

@Path("locations")
@ApplicationScoped
public class LocationResource {

    @Inject
    KafkaAdapter kafka;

    @Inject
    ObjectMapper om;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response get() {

        2118  curl -X GET "https://service.post.ch/zopa/app/api/addresschecker/v1/zips?noCache=1556451724044&city=*&limit=60&zip=1607*"
        2119  curl -X GET "https://service.post.ch/zopa/app/api/addresschecker/v1/houses/search?noCache=1556451675078&city=Pal%C3%A9zieux*&houseNbr=26*&limit=60&street=marou*"
        2120  curl -X GET "https://service.post.ch/zopa/app/api/addresschecker/v1/houses/search?noCache=1556451675078&city=Pal%C3%A9zieux*&limit=60&street=marou*26"
        2121  curl -X GET "https://service.post.ch/zopa/app/api/addresschecker/v1/houses/search?noCache=1556451675078&city=Pal%C3%A9zieux*&houseNbr=26*&limit=60&street=marou*"
        2122  curl -X GET "https://service.post.ch/zopa/app/api/addresschecker/v1/houses/search?noCache=1556451675078&city=Pal%C3%A9zieux*&houseNbr=&limit=60&street=marou*"


        return Response.ok(claimRepository.getClaims()).build();
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(Claim claim) throws JsonProcessingException {

        ProducerRecord<String, String> claimDbRecord = new ProducerRecord<>(CLAIM_CREATE_DB_COMMAND_TOPIC, null, null, om.writeValueAsString(claim));
        ProducerRecord<String, String> claimSearchRecord = new ProducerRecord<>(CLAIM_CREATE_SEARCH_COMMAND_TOPIC, null, null, om.writeValueAsString(claim));
        ProducerRecord<String, String> claimRelationshipRecord = new ProducerRecord<>(CLAIM_CREATE_RELATIONSHIP_COMMAND_TOPIC, null, null, om.writeValueAsString(claim));
        ProducerRecord<String, String> createTaskCommand = new ProducerRecord<>(TASK_CREATE_COMMAND_TOPIC, null, null, om.writeValueAsString(new Task(claim.getId(), "call customer " + claim.getPartnerId())));
        kafka.sendInOneTransaction(asList(claimDbRecord, claimSearchRecord, claimRelationshipRecord, createTaskCommand));

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
