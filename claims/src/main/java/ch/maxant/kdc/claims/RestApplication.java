package ch.maxant.kdc.claims;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;
import java.nio.charset.StandardCharsets;

@ApplicationPath("/rest")
public class RestApplication extends Application {

    public static final String COMPONENT_NAME = "claims";
    public static final byte[] COMPONENT_NAME_BYTES = COMPONENT_NAME.getBytes(StandardCharsets.UTF_8);

    public static final String KAFKA_NAME = "kafka";
    public static final byte[] KAFKA_NAME_BYTES = KAFKA_NAME.getBytes(StandardCharsets.UTF_8);
}