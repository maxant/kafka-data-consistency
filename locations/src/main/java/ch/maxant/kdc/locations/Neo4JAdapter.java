package ch.maxant.kdc.locations;

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

    public void createLocation(Location location) {
        switch (location.getType()) {
            case CLAIM_LOCATION:
                createLocationAndRelationship("Claim", location);
                break;
            default: throw new IllegalArgumentException("unexpected type: " + location.getType());
        }
    }

    public void createLocationAndRelationship(String type, Location location) {

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

}

