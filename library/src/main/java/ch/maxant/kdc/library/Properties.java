package ch.maxant.kdc.library;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.eclipse.microprofile.config.Config;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.toMap;

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
        props.putAll(getEnv());
    }

    /** gets the environment properties, but changes keys from having underscores to having dots */
    public Map<String, String> getEnv() {
        return System.getenv().entrySet().stream()
                .map(e -> new ImmutablePair<>(
                    e.getKey().replaceAll("_", "."), e.getValue()
                ))
                .collect(toMap(e -> e.left, e -> e.right));
    }

    public String getProperty(String name) {
        String p = props.get(name);
        if(p == null) {
            throw new NullPointerException("unknown property " + name);
        }
        return p;
    }

    public String getProperty(String name, String default_) {
        String p = props.get(name);
        if(p == null) {
            p = default_;
        }
        return p;
    }

    public Optional<String> getOptionalProperty(String name) {
        return Optional.ofNullable(props.get(name));
    }

}
 