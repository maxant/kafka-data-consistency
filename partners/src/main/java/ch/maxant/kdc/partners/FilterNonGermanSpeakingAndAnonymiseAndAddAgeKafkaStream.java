// original from here: https://raw.githubusercontent.com/confluentinc/kafka-streams-examples/5.3.0-post/src/main/java/io/confluent/examples/streams/MapFunctionLambdaExample.java
package ch.maxant.kdc.partners;

import ch.maxant.kdc.library.JacksonConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Throws out non-german-speakering citizens (keeps only CH/DE/AT).
 * Anonymises last name (personally identifiable information aka PII).
 * Adds age.
 */
public class FilterNonGermanSpeakingAndAnonymiseAndAddAgeKafkaStream {

    private static final ObjectMapper om = JacksonConfig.getMapper();
    private static final Set<Integer> GERMAN_SPEAKING_COUNTRY_CODES;

    static {
        GERMAN_SPEAKING_COUNTRY_CODES = new HashSet<Integer>(){{
            add(756); // CH
            add( 40); // AT
            add(276); // DE
        }};
    }

    public static void main(final String[] args) {
        final String bootstrapServers = args.length > 0 ? args[0] : "maxant.ch:30001,maxant.ch:30002";
        final Properties streamsConfiguration = new Properties();
        streamsConfiguration.put(StreamsConfig.APPLICATION_ID_CONFIG, "partners-filter-swiss-anonymise-and-add-age");
        streamsConfiguration.put(StreamsConfig.CLIENT_ID_CONFIG, "partners-filter-swiss-anonymise-and-add-age-client");
        streamsConfiguration.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        streamsConfiguration.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        streamsConfiguration.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());

        final Serde<String> stringSerde = Serdes.String();

        StreamsBuilder builder = new StreamsBuilder();
        builder.stream("partner-created-event", Consumed.with(stringSerde, stringSerde))
            .peek((k,p) -> printf("input partner %s", p))
            .map((k,v) -> KeyValue.pair(k, mapToPartner(v))) // deserialise json
            .filter((k,p) -> { // filter only swiss
                boolean keep = GERMAN_SPEAKING_COUNTRY_CODES.contains(p.getNationality());
                printf("keeping %s: %s", p.getId(), keep);
                return keep;
            })
            .mapValues((k,p) -> { // anonymise and add age
                // we could also call a REST service here to enrich the data
                p = new Partner(p.getFirstname(), "*****", p.getNationality(), p.getDateOfBirth());

                p.setAge(Period.between(p.getDateOfBirth(), LocalDate.now()).getYears());
                return p;
            })
            .mapValues(FilterNonGermanSpeakingAndAnonymiseAndAddAgeKafkaStream::toJsonString)
            .peek((k,p) -> printf("output partner %s", p))
            .to("partners-created-swiss-anonymous-with-age");

        final KafkaStreams streams = new KafkaStreams(builder.build(), streamsConfiguration);
        streams.cleanUp();
        streams.start();

        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    }

    private static void printf(String msg, Object... args) {
        List<Object> list = new ArrayList(Arrays.asList(args));
        list.add(0, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        list.add(0, Thread.currentThread().getName());
        System.out.printf("%s - %s - " + msg + "\n", list.toArray());
    }

    private static Partner mapToPartner(String value) {
        try {
            return om.readValue(value, Partner.class);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static String toJsonString(Partner p) {
        try {
            return om.writeValueAsString(p);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

}