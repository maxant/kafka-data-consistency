package ch.maxant.kdc.claims;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class TaskAdapter {

    @Inject
    @RestClient
    TaskService taskService;

    /**
     * An example where some synchronous (online) validation is needed upfront. we still send the data
     * async via kafka, because we want all writes to be done in the same transaction (which kafka provides).
     * The tasks application should give us a guarantee that the data will be accepted when the kafka record
     * to write the data later arrives.
     */
    public void validate(Task task) {
        TaskValidationResult result = taskService.validate(task);
        if(!result.isValid()) {
            throw new IllegalArgumentException("task isn't valid!");
        }
    }
}
