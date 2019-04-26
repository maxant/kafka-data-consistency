package ch.maxant.kdc.claims;

import ch.maxant.kdc.library.Properties;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.math.BigDecimal;
import java.sql.*;

@ApplicationScoped // TODO use JPA and native queries => maps to classes :-)
public class Neo4JAdapter {

    @Inject
    Properties properties;

    @PostConstruct
    public void init() {


    }

    @PreDestroy
    public void shutdown() {
    }

    public void createClaim(Claim claim) {
        //https://github.com/neo4j-contrib/neo4j-jdbc

        String url = properties.getProperty("neo4j.jdbc.url");
        String user = properties.getProperty("neo4j.jdbc.username");
        String pass = properties.getProperty("neo4j.jdbc.password");

        String query = "MATCH (p:Partner) WHERE p.id = ? " +
                       "CREATE (c:Claim { id: ?, date: ? }), " +
                       "       (p)-[:CLAIMANT]->(c)";
        try (Connection con = DriverManager.getConnection(url, user, pass);
             PreparedStatement stmt = con.prepareStatement(query)
        ) {
            stmt.setString(1, claim.getCustomerId());
            stmt.setString(2, claim.getId());
            stmt.setString(3, claim.getDate());
            long start = System.currentTimeMillis();
            if (stmt.execute()) {
                System.out.println("get resultset in " + (System.currentTimeMillis() - start) + "ms");
                try(ResultSet rs = stmt.getResultSet()) {
                    ResultSetMetaData metaData = stmt.getMetaData();
                    for(int i = 0; i < metaData.getColumnCount(); i++) {
                        System.out.println("Got column: " + metaData.getColumnName(i));
                    }
                    while (rs.next()) {
                        System.out.println("got a row!!: " + rs.getString(1));
                    }
                }
            } else {
                System.out.println("Updated " + stmt.getUpdateCount() + " rows in " + (System.currentTimeMillis() - start) + "ms");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        Neo4JAdapter adapter = new Neo4JAdapter();
        adapter.properties = new Properties(){
            @Override
            public String getProperty(String name) {
                if(name.equals("neo4j.jdbc.url")) {
                    return "jdbc:neo4j:bolt://kdc.neo4j.maxant.ch:30101";
                } else if(name.equals("neo4j.jdbc.username")) {
                    return "";
                } else if(name.equals("neo4j.jdbc.password")) {
                    return "";
                }
                throw new RuntimeException("unexpected name " + name);
            }
        };
        adapter.init();

        Claim claim = new Claim();
        claim.setCustomerId("C-4837-4536");
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
