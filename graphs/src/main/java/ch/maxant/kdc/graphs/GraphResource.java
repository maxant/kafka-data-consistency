package ch.maxant.kdc.graphs;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.Path;

@Path("graphs")
@ApplicationScoped
public class GraphResource {

    @Inject
    ObjectMapper om;

    @Inject
    Neo4JAdapter neo4JAdapter;

    //TODO build standard neo4j queries here

}
