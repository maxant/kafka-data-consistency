package ch.maxant.kdc.locations;

import ch.maxant.kdc.library.Properties;
import org.neo4j.jdbc.bolt.BoltDriver;

import javax.inject.Inject;
import java.math.BigDecimal;

@Stateless
public class Neo4JAdapter {

    @PersistenceContext(unitName = "neo4j")
    private EntityManager em;

    public void createClaim(Claim claim) {
        //https://github.com/neo4j-contrib/neo4j-jdbc
        String query = "MATCH (p:Partner) WHERE p.id = ? " +
                       "CREATE (c:Claim { id: ?, date: ? }), " +
                       "       (p)-[:CLAIMANT]->(c)";
    }

}
