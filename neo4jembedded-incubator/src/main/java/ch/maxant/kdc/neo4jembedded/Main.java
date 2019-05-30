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

                // from: from when is the contract valid?
                // to:   until when is the contract valid?
                // created: when did the user create the node?
                // expires: when does the node exist until, because it was replaced by another more up to date one?
                //
                // this allows you to look at the history and see what kind of validity the customer had on a particular day
                // e.g. before a user changed something.
                //
                // eg (not sure my understanding is the same as the customers!), a contract replacement, 4 was created on
                // the 1st june 2018 to replace contract 3. at the time, it was planned to be valid from 2st june
                //
                // (1)-->(2)-------------------------------------------->
                //     \->(3)-->(4, g4 was corrected on june 15th)--X
                //         \--->(4, aka g5)->(8)------------------------>
                //                 (6)-->(7)-/
                //                   (9)-/
                // 2017-05-01 g1 created
                // 2018-01-01 g1 split into g2 & g3
                // 2018-02-01 g6 created
                // 2018-04-01 g9 created
                // 2018-06-01 g4 created, replacing g3, but "nicht zu stande gekommen" => effectively never created from a business point of view
                // 2018-06-15 g5 created in lieu of g4, replacing g3
                // 2018-07-01 g7 created, merging g6 & g9
                // 2018-12-01 g8 created, merging g5 & g7
                //
                // when a contract is replaced by another, we need to update its "to" attribute.
                // when a correction is made, we need to update the expires attribute of the node being corrected ie. technically replaced by another node.
                // also need to add relationships to make it all fit together correctly
                //
                tx2.run(
                        "CREATE (g1:Contract { number: 1, from: '2017-05-01', to: '2017-12-31', created: '2017-05-01', expires: '9999-12-31' })," +
                               "(g2:Contract { number: 2, from: '2018-01-01', to: '9999-12-31', created: '2017-12-01', expires: '9999-12-31' })," +
                               "(g3:Contract { number: 3, from: '2018-01-01', to: '2018-06-01', created: '2017-12-01', expires: '9999-12-31' })," +
                               "(g6:Contract { number: 6, from: '2018-02-01', to: '2018-06-31', created: '2018-02-01', expires: '9999-12-31' })," +
                               "(g9:Contract { number: 9, from: '2018-04-01', to: '2018-06-31', created: '2018-04-01', expires: '9999-12-31' })," +
                               "(g4:Contract { number: 4, from: '2018-06-02', to: '9999-12-31', created: '2018-06-01', expires: '2018-06-15' })," + // does this one get its "to" attribute updated when its replaced by g8?
                               "(g5:Contract { number: 4, from: '2018-06-02', to: '9999-12-31', created: '2018-06-15', expires: '9999-12-31' })," +
                               "(g7:Contract { number: 7, from: '2018-07-01', to: '2018-11-30', created: '2018-06-29', expires: '9999-12-31' })," +
                               "(g8:Contract { number: 8, from: '2018-12-01', to: '9999-12-31', created: '2018-12-01', expires: '9999-12-31' })," +
                               "(g1)-[:replacedBy]->(g2)," +
                               "(g1)-[:replacedBy]->(g3)," +
                               "(g3)-[:replacedBy]->(g4)," +
                               "(g3)-[:replacedBy]->(g5)," +
                               "(g5)<-[:technicalReplacementOf]-(g4)," + // are such relationships interesting?
                               "(g6)-[:replacedBy]->(g7)," +
                               "(g9)-[:replacedBy]->(g7)," +
                               "(g5)-[:replacedBy]->(g8)," +
                               "(g7)-[:replacedBy]->(g8)"
                );
                tx2.success();
            }
            try(Transaction tx2 = session.beginTransaction()) {
                StatementResult result = tx2.run(
                        "match (n:Contract)-[r]-(n2) where n.created < '2018-01-01' AND n.expires >= '2018-01-01' return *"
                );
                while(result.hasNext()) {
                    Record r = result.next();
                    System.out.println(r.asMap());
                }
                tx2.success();
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
