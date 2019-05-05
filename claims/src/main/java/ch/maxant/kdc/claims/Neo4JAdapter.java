package ch.maxant.kdc.claims;

import ch.maxant.kdc.library.Properties;
import ch.maxant.kdc.library.RawNeo4JAdapter;
import org.neo4j.driver.v1.summary.ResultSummary;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
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
            throw new RuntimeException("didnt create result as expected! " + result);
        }
    }

    public static void main(String[] args) {
        RawNeo4JAdapter a = new RawNeo4JAdapter();
        a.properties = new Properties() {
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
        };
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
