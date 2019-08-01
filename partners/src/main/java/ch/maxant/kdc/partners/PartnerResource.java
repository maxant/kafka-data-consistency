package ch.maxant.kdc.partners;

import ch.maxant.kdc.library.KafkaAdapter;
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

import static java.util.Collections.singletonList;

@Path("partners")
@ApplicationScoped
public class PartnerResource {

    @Inject
    KafkaAdapter kafka;

    @Inject
    ObjectMapper om;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(Partner partner) throws JsonProcessingException {

        ProducerRecord<String, String> record = new ProducerRecord<>("ksql-test-cud-partners", null, partner.getId(), om.writeValueAsString(partner));
        kafka.sendInOneTransaction(singletonList(record));

        return Response.accepted().build();
    }
}
