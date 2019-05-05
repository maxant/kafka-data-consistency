package ch.maxant.kdc.library;

import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Scope;
import co.elastic.apm.api.Span;
import org.neo4j.driver.v1.*;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Map;

@ApplicationScoped
public class RawNeo4JAdapter {

    @Inject
    Properties properties;

    private Driver driver;

    @PostConstruct
    public void init() {
        String url = "bolt://" + properties.getProperty("NEO4J_HOST") + ":" + properties.getProperty("NEO4J_PORT");
        String user = properties.getProperty("NEO4J_USER");
        String password = properties.getProperty("NEO4J_PASSWORD");
        driver = GraphDatabase.driver(url, AuthTokens.basic(user, password));
    }

    @PreDestroy
    public void close() {
        driver.close();
    }

    public StatementResult runInTx(String query, Map<String, Object> params) {

        co.elastic.apm.api.Transaction transaction = ElasticApm.currentTransaction();
        try (Scope scope = transaction.activate()) {
            final Span span = ElasticApm.currentSpan().startSpan("neo4j", "query", "run").setName(query);
            try (Scope spanScope = span.activate()) {

                try (Session session = driver.session()) {
                    try(Transaction tx = session.beginTransaction()) {
                        StatementResult result = tx.run(query, params);
                        tx.success();
                        return result;
                    }
                }

            } catch (Exception e) {
                span.captureException(e);
                throw e;
            } finally {
                span.end();
            }
        }
    }
}
