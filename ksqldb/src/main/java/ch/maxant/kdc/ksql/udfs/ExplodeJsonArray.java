package ch.maxant.kdc.ksql.udfs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.confluent.ksql.function.udf.UdfParameter;
import io.confluent.ksql.function.udtf.Udtf;
import io.confluent.ksql.function.udtf.UdtfDescription;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@UdtfDescription(name = "explodeJsonArray", description = "explodes a json array because default EXPLODE cant do it")
public class ExplodeJsonArray {

    ObjectMapper mapper = ObjectMapperProvider.getInstance();

    @Udtf(description = "As per description")
    public List<String> doit(@UdfParameter(value = "jsons") String array) throws IOException {
        ArrayNode nodes = (ArrayNode) mapper.readTree(array);
        List<String> output = new ArrayList<>(nodes.size());
        for(JsonNode node : nodes) {
            output.add(mapper.writeValueAsString(node));
        }
        return output;
    }

    public static void main(String[] args) throws IOException {
        System.out.println(new ExplodeJsonArray().doit("[{\"a\": 1}, 2]"));
    }
}