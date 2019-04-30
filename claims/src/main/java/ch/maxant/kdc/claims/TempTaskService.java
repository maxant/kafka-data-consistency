package ch.maxant.kdc.claims;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.client.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

// not part of the final design.
@ApplicationScoped
public class TempTaskService {


    public void createTask(Task task) {
        Client client = ClientBuilder.newClient();
        WebTarget webTarget = client.target("http://localhost:8082/tasks/rest/");
        webTarget = webTarget.path("tasks");
        Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON);
        try(Response response = invocationBuilder.post(Entity.json(task))) {
            // TODO do we need to do this, or is it already closed and would throw an exception on error?
            if(!response.getStatusInfo().getFamily().equals(Response.Status.Family.SUCCESSFUL)){
                throw new RuntimeException("failed to create task " + response.getStatusInfo());
            }
        }
    }
}
