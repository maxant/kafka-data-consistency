package ch.maxant.kdc.claims;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;

@ApplicationScoped
public class TaskService {

    @Inject
    @ConfigProperty(name="tasks.baseurl")
    private String tasksBaseUrl;

    /**
     * An example where some synchronous (online) validation is needed upfront. we still send the data
     * async via kafka, because we want all writes to be done in the same transaction (which kafka provides).
     * The tasks application should give us a guarantee that the data will be accepted when the kafka record
     * to write the data later arrives.
     */
    public void validate(Task task) {
        Client client = ClientBuilder.newClient();
        TaskValidationResult result = client.target(tasksBaseUrl)
                .path("tasks/validate")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(task), TaskValidationResult.class);
        if(!result.isValid()) {
            throw new IllegalArgumentException("task isn't valid!");
        }
    }

    public static void main(String[] args) {
        TaskService ts = new TaskService();
        ts.tasksBaseUrl = "http://localhost:8082/tasks/rest/tasks/validate";
        ts.validate(new Task("asdf", "valid"));
    }
}
