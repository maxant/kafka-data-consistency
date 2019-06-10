package ch.maxant.kdc.graphs;

import ch.maxant.kdc.library.Properties;
import org.neo4j.driver.v1.summary.ResultSummary;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class Neo4JAdapter {

    @Inject
    RawNeo4JAdapter adapter;

    public void createClaim(Claim claim) {

        String query = "MATCH (p:Partner) WHERE p.id = $partnerId " +
                       "CREATE (c:Claim { id: $claimId, date: $date }), " +
                       "       (p)-[:CLAIMANT]->(c)";

        Map<String, Object> params = new HashMap<>();
        params.put("partnerId", claim.getPartnerId());
        params.put("claimId", claim.getId());
        params.put("date", claim.getDate());

        ResultSummary result = adapter.runInTx(query, params).consume();
        if(1 != result.counters().nodesCreated() && 1 != result.counters().relationshipsCreated()) {
            throw new RuntimeException("didnt create claim as expected! " + result);
        }
    }

    public void createLocation(Location location) {
        switch (location.getType()) {
            case CLAIM_LOCATION:
                createLocation("Claim", location);
                break;
            default: throw new IllegalArgumentException("unexpected type: " + location.getType());
        }
    }

    private void createLocation(String type, Location location) {

        String query = "MATCH (x:" + type + ") WHERE x.id = $aggregateId " +
                       "CREATE (l:Location { zip: $zip, city: $city }), " +
                       "       (l)-[:LOCATION_OF]->(x)";

        Map<String, Object> params = new HashMap<>();
        params.put("aggregateId", location.getAggretateId());
        params.put("zip", location.getZip());
        params.put("city", location.getCity());

        ResultSummary result = adapter.runInTx(query, params).consume();
        if(1 != result.counters().nodesCreated() && 1 != result.counters().relationshipsCreated()) {
            throw new RuntimeException("didnt create result as expected! " + result);
        }
    }

    public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {
        RawNeo4JAdapter a = new RawNeo4JAdapter();
        Field f = a.getClass().getDeclaredField("properties");
        f.setAccessible(true);
        f.set(a, new Properties() {
            @Override
            public String getProperty(String name) {
                switch (name) {
                    case "NEO4J_HOST": return "kdc.neo4j.maxant.ch";
                    case "NEO4J_PORT": return "30101";
                    case "NEO4J_USER": return "a";
                    case "NEO4J_PASSWORD": return "a";
                    default: throw new IllegalArgumentException(name);
                }
            }
        });
        a.init();
        Claim claim = new Claim();
        claim.setDate("2016-08-24");
        claim.setPartnerId("C-1234-5678");
        claim.setId("claimId-" + System.currentTimeMillis());

        Neo4JAdapter n = new Neo4JAdapter();
        n.adapter = a;
        n.createClaim(claim);
        a.close();
    }
}
