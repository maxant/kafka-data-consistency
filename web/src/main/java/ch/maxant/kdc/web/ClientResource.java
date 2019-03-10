package ch.maxant.kdc.web;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("test")
@ApplicationScoped
public class ClientResource {

    // see also https://www.eclipse.org/community/eclipse_newsletter/2017/september/article3.php
    @Inject
    @ConfigProperty(name = "default.property", defaultValue = "Default Value")
    String defaultProperty;

    @Inject
    WebSocketModel clients;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String helloWorld() {
        clients.sendToAll("{\"aValue\": 123}");
        return "World " + defaultProperty;
    }

}