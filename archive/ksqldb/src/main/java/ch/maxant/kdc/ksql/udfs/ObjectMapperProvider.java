package ch.maxant.kdc.ksql.udfs;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.TimeZone;

public class ObjectMapperProvider {
    private static final ObjectMapper INSTANCE = new ObjectMapper();

    public ObjectMapperProvider() {
    }

    public static ObjectMapper getInstance() {
        return INSTANCE;
    }

    public ObjectMapper get() {
        return INSTANCE;
    }

    static {
        INSTANCE.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        INSTANCE.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        INSTANCE.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        INSTANCE.setTimeZone(TimeZone.getTimeZone("UTC"));
        INSTANCE.findAndRegisterModules();
    }
}
