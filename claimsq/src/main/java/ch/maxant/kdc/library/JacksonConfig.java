package ch.maxant.kdc.library;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

@ApplicationScoped
@Provider
@Produces(MediaType.APPLICATION_JSON)
public class JacksonConfig implements ContextResolver<ObjectMapper> {

    private ObjectMapper objectMapper;

    public JacksonConfig() {
        this.objectMapper = new ObjectMapper();

        this.objectMapper.registerModule(new Jdk8Module());
        this.objectMapper.registerModule(new JavaTimeModule());

        this.objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        this.objectMapper.configure(JsonGenerator.Feature.ESCAPE_NON_ASCII, true); //convert non ascii to utf-8
    }

    public ObjectMapper getContext(Class<?> objectType) {
        return objectMapper;
    }

    public static ObjectMapper getMapper() {
        try {
            return new JacksonConfig().objectMapper;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @javax.enterprise.inject.Produces
    public ObjectMapper getObjectMapper() {
        return this.objectMapper;
    }
}
 