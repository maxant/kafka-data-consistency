package ch.maxant.kdc.partners;


import ch.maxant.kdc.library.KafkaAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;

//@Path("partners")
//@ApplicationScoped
public class PartnerResource {

//    @Inject
    KafkaAdapter kafka;

//    @Inject
    ObjectMapper om;
/*
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(Partner partner) throws JsonProcessingException {

        ProducerRecord<String, String> record = new ProducerRecord<>("ksql-test-cud-partners", null, partner.getId(), om.writeValueAsString(partner));
        kafka.sendInOneTransaction(singletonList(record));

        return Response.accepted().build();
    }
*/
}
