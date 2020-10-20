package ch.maxant.kdc.mf.contracts.library

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.json.JsonWriteFeature
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.quarkus.jackson.ObjectMapperCustomizer
import javax.inject.Singleton

@Singleton
public open class JacksonConfig : ObjectMapperCustomizer {

    override fun customize(mapper: ObjectMapper) {
        mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
        mapper.enable(JsonWriteFeature.ESCAPE_NON_ASCII.mappedFeature()) //convert non ascii to utf-8
        mapper.disable(SerializationFeature.INDENT_OUTPUT);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // register kotlin module for data classes
        // cant find this in maven yet: mapper.registerModule(KotlinModule())

        mapper.registerModule(Jdk8Module())
        mapper.registerModule(JavaTimeModule())
    }

    companion object {

        val om = ObjectMapper()

        init {
            JacksonConfig().customize(om)
        }
    }
}
