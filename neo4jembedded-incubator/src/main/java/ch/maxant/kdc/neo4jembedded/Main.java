package ch.maxant.kdc.neo4jembedded;

import org.neo4j.driver.v1.*;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.kernel.configuration.BoltConnector;
import org.neo4j.test.TestGraphDatabaseFactory;

import java.io.File;

public class Main {

    private static final String hostport = "localhost:7687";

    public static void main(String[] args) throws Exception {

        long start = System.currentTimeMillis();
        GraphDatabaseService neo = initTestNeoWithFile();
        System.out.format("initialised embedded in-memory no-file test neo4j in %s ms\n", System.currentTimeMillis() - start);

        String url = "bolt://" + hostport;
        Driver driver = GraphDatabase.driver(url); //, AuthTokens.basic(user, password));

        try (Session session = driver.session()) {
            try(Transaction tx2 = session.beginTransaction()) {
                StatementResult result = tx2.run("CREATE (ee:Person { name: \"Emil\", from: \"Sweden\", klout: 99 }),(js:Person { name: \"Johan\", from: \"Sweden\", learn: \"surfing\" }),(ee)-[:KNOWS {since: 2001}]->(js)");
                tx2.success();
                System.out.println(result);
            }
            try(Transaction tx2 = session.beginTransaction()) {
                StatementResult result = tx2.run("match (n) return *");
                tx2.success();
                System.out.println(result);
            }
        }

        while(true) {
            Thread.sleep(1000);
        }
        //neo.shutdown();

    }

    private static GraphDatabaseService initTestNeoWithFile() {
        BoltConnector bolt = new BoltConnector("0");
        return new TestGraphDatabaseFactory().newImpermanentDatabaseBuilder()
                .setConfig(bolt.type, "BOLT")
                .setConfig(bolt.enabled, "true")
                .setConfig(bolt.listen_address, hostport)
//                .setConfig(bolt.encryption_level, BoltConnector.EncryptionLevel.DISABLED.toString())
//                .setConfig(GraphDatabaseSettings.auth_enabled, "false")
                .newGraphDatabase();

    }

    private static GraphDatabaseService initNeoWithFile() {

        BoltConnector bolt = new BoltConnector("0");
        return new GraphDatabaseFactory()
                .newEmbeddedDatabaseBuilder(new File("./neo4jembedded-incubator/neo4jstore"))
                .setConfig(bolt.type, "BOLT")
                .setConfig(bolt.enabled, "true")
                .setConfig(bolt.listen_address, hostport)
//                .setConfig(bolt.encryption_level, BoltConnector.EncryptionLevel.DISABLED.toString())
//                .setConfig(GraphDatabaseSettings.auth_enabled, "false")
                .newGraphDatabase();
    }
}
