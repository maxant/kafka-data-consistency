package ch.maxant.kdc.tasks;

import javax.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class Model {

    Map<String, List<String>> tasks = new ConcurrentHashMap<>();

    public Map<String, List<String>> getTasks() {
        return tasks;
    }
}
