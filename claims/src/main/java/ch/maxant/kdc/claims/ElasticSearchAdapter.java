package ch.maxant.kdc.claims;

import ch.maxant.kdc.library.JacksonConfig;
import ch.maxant.kdc.library.Properties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHost;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.replication.ReplicationResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.rest.RestStatus;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.math.BigDecimal;

@ApplicationScoped // only need one, as rest client is thread safe - see commdsn below
public class ElasticSearchAdapter {

    // javadocs dont mention thread safety as far as i can see, but this post does:
    // https://discuss.elastic.co/t/java-rest-client-and-multithreading/123298
    // this too:
    // https://www.elastic.co/guide/en/elasticsearch/client/java-rest/6.7/_changing_the_client_8217_s_initialization_code.html
    // yes, its thread safe!
    RestHighLevelClient client;

    @Inject
    Properties properties;

    @Inject
    ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        String baseurl = properties.getProperty("elasticsearch.baseUrl");

        // for prod we'd add a list of hosts here, and use https rather than http
        client = new RestHighLevelClient(RestClient.builder(HttpHost.create(baseurl)));
    }

    @PreDestroy
    public void shutdown() {
        try {
            client.close();
        } catch (IOException e) {
            System.err.println("error during closing of elasticsearch client");
            e.printStackTrace(); // TODO error handling
        }
    }

    public void createClaim(Claim claim) {
        try {
            IndexRequest request = new IndexRequest("claims");
            request.id(claim.getId());
            request.routing(claim.getPartnerId()); // so that all partner data lands on the same shard
            request.source(objectMapper.writeValueAsString(claim), XContentType.JSON);
            request.opType(DocWriteRequest.OpType.CREATE); // causes ElasticsearchException if already exists
            IndexResponse indexResponse = client.index(request, RequestOptions.DEFAULT);
            System.out.println("Created claim in ES: " + indexResponse);

            if (indexResponse.getResult() != DocWriteResponse.Result.CREATED) {
                throw new RuntimeException("unexpected result: " + indexResponse.getResult() + " from ES while creating claim " + claim.getId());
            }
            ReplicationResponse.ShardInfo shardInfo = indexResponse.getShardInfo();
            if (shardInfo.getTotal() != shardInfo.getSuccessful()) {
                System.out.println("warning: not all shards created the document: " + shardInfo);
            }
            if (shardInfo.getFailed() > 0) {
                for (ReplicationResponse.ShardInfo.Failure failure :
                        shardInfo.getFailures()) {
                    String reason = failure.reason();
                    System.out.println("warning: failure on shard {" + shardInfo + "}: " + reason);
                }
                // assume its ok to continue, as result was "CREATED", so at least one shard has the data, no?
            }
        } catch (ElasticsearchException e) {
            if (e.status() == RestStatus.CONFLICT) {
                System.err.println("couldnt create claim with Id " + claim.getId() + " because it already exists in ES");
            }
            throw e;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        ElasticSearchAdapter adapter = new ElasticSearchAdapter();
        adapter.objectMapper = JacksonConfig.getMapper();
        adapter.properties = new Properties(){
            @Override
            public String getProperty(String name) {
                if(name.equals("elasticsearch.baseUrl")) {
                    return "http://kdc.elasticsearch.maxant.ch:30050";
                }
                throw new RuntimeException("unexpected name " + name);
            }
        };
        adapter.init();

        Claim claim = new Claim();
        claim.setPartnerId("P-1234-5678");
        claim.setSummary("Der Flieger flog zu schnell");
        claim.setDescription("A random act of nature caused a jolt leaving me with soup on my tie!");
        claim.setReserve(new BigDecimal("132.90"));
        claim.setDate("2018-08-05");
        System.out.println("creating claim: " + claim.getId());

        try {
            adapter.createClaim(claim);
        } finally {
            adapter.shutdown();
        }
    }
}
