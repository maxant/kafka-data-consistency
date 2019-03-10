package ch.maxant.kdc.library;

import org.eclipse.microprofile.config.Config;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

/** TODO is this necessary? or does microprofile also read env? */
@ApplicationScoped
public class Properties {

    private Map<String, String> props = new HashMap<>();

    @Inject
    Config config;

    @PostConstruct
    public void init() {
        // system props beats environment beats config from file
        config.getPropertyNames().forEach(n -> props.put(n, config.getValue(n, String.class)));
        // not necessary, as already in config: System.getProperties().stringPropertyNames().forEach(n -> props.put(n, System.getProperty(n)));
        props.putAll(System.getenv());
    }

    public String getProperty(String name) {
        String p = props.get(name);
        if(p == null) {
            throw new NullPointerException("unknown property " + name);
        }
        return p;
    }

}
 