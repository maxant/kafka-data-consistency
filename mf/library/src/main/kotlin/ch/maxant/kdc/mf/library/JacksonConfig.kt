package ch.maxant.kdc.mf.library

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.core.json.JsonWriteFeature
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.quarkus.jackson.ObjectMapperCustomizer
import javax.inject.Singleton


@Singleton
public open class JacksonConfig : ObjectMapperCustomizer {

    override fun customize(mapper: ObjectMapper) {

        //allow deser of anysubclasses
        val bptv = BasicPolymorphicTypeValidator.builder()
                //.allowIfSubTypeIsArray()
                .allowIfBaseType("ch.maxant")
                .allowIfSubType("ch.maxant")
                .build()

        var typer: TypeResolverBuilder<*> = ObjectMapper.DefaultTypeResolverBuilder.construct(
                ObjectMapper.DefaultTyping.NON_CONCRETE_AND_ARRAYS, bptv)
        typer = typer.init(JsonTypeInfo.Id.MINIMAL_CLASS, null)
        typer = typer.inclusion(JsonTypeInfo.As.PROPERTY)
        typer = typer.typeProperty("c*c")

        mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
                .enable(JsonWriteFeature.ESCAPE_NON_ASCII.mappedFeature())
                .disable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .registerModule(KotlinModule())
                .registerModule(Jdk8Module())
                .registerModule(JavaTimeModule())
                .setDefaultTyping(typer)
//                .activateDefaultTypingAsProperty(PTV(), ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE, "@c")
    }

    companion object {

        val om = ObjectMapper()

        init {
            JacksonConfig().customize(om)
        }
    }
}
