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
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Throws out non-german-speaking citizens (keeps only CH/DE/AT).
 * Anonymises last name (personally identifiable information aka PII).
 * Adds age.
 */
public class FilterNonGermanSpeakingAndAnonymiseAndAddAgeKafkaStream implements Topics {

    private static final ObjectMapper om = JacksonConfig.getMapper();

    private static final Set<Integer> GERMAN_SPEAKING_COUNTRY_CODES = new HashSet<Integer>(){{
        add(756); // CH
        add( 40); // AT
        add(276); // DE
    }};
    private static final String COUNT_BY_PARTNER_COUNTRY_STORE_NAME = "CountByPartnerCountryStoreName";
    private static final String GLOBAL_COUNT_BY_PARTNER_COUNTRY_STORE_NAME = "GlobalCountByPartnerCountryStoreName";
    private static final String GLOBAL_PARTNERS_STORE_NAME = "GlobalPartnersStoreName";

    public static void main(final String[] args) {
        final String bootstrapServers = args.length > 0 ? args[0] : "maxant.ch:30001,maxant.ch:30002";
        final Properties streamsConfiguration = new Properties();
        streamsConfiguration.put(StreamsConfig.APPLICATION_ID_CONFIG, "partner-filter-german-speaking-anonymise-and-add-age");
        streamsConfiguration.put(StreamsConfig.CLIENT_ID_CONFIG, "partner-filter-german-speaking-anonymise-and-add-age-client");
        streamsConfiguration.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        streamsConfiguration.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        streamsConfiguration.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        streamsConfiguration.put(StreamsConfig.STATE_DIR_CONFIG, "/tmp/kafka-streams/partner/" + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).replace("-", "").replace(":", ""));

        final Serde<String> stringSerde = Serdes.String();

        StreamsBuilder builder = new StreamsBuilder();

        // build the global ktable which can be queried from any instance to find counts for all countries
        GlobalKTable<String, String> countByCountryGkt = builder.globalTable(PARTNER_CREATED_GERMAN_SPEAKING_GLOBAL_COUNT, Materialized.as(GLOBAL_COUNT_BY_PARTNER_COUNTRY_STORE_NAME));

        KStream<String, Partner> partnerCreatedStream = builder.stream(PARTNER_CREATED_EVENT, Consumed.with(stringSerde, stringSerde))
                .map((k, v) -> KeyValue.pair(k, mapToPartner(v))) // deserialise json
                .mapValues(p -> new Partner(p.getId(), p.getFirstname(), "*****", p.getNationality(), p.getDateOfBirth())) // anonymise
        ;

        // stick raw anonymous partners on a topic that we can create a global ktable from, to see how it performs
        partnerCreatedStream
            .mapValues(FilterNonGermanSpeakingAndAnonymiseAndAddAgeKafkaStream::toJsonString)
            .to(PARTNER_CREATED_ANONYMOUS);
        ;

        // but also filter that stream and play with joins, etc.
        partnerCreatedStream
                //.peek((k, p) -> printf("input partner %s", p))
                .filter((k, p) -> { // filter only german speakers
                    boolean keep = GERMAN_SPEAKING_COUNTRY_CODES.contains(p.getNationality());
                    //printf("keeping %s: %s", p.getId(), keep);
                    return keep;
                })
                .mapValues((k, p) -> { // anonymise and add age
                    // we could also call a REST service here to enrich the data
                    p.setAge(Period.between(p.getDateOfBirth(), LocalDate.now()).getYears());
                    return p;
                })
                .leftJoin( // watch out! if just using join, we have a chicken egg situation with this toy example, because the gkt doesnt contain any data until this stream processes data completely!
                        countByCountryGkt, // the global ktable to join from
                        (k,p)-> PartnerGenerator.COUNTRY_CODES.get(p.getNationality()), // the key to use to lookup in the global table
                        (p,countryCount) -> { // join the value into the record begin processed
                            System.out.printf("we could join country count %s into the partner %s from %s\n", countryCount, p.getId(), p.getNationality());
                            return p;
                        })
                .mapValues(FilterNonGermanSpeakingAndAnonymiseAndAddAgeKafkaStream::toJsonString)
                //.peek((k, p) -> printf("output partner %s", p))
                .to(PARTNER_CREATED_GERMAN_SPEAKING_ANONYMOUS_WITH_AGE)
        ;

        KGroupedStream<String, String> countByCountry = builder.stream(PARTNER_CREATED_GERMAN_SPEAKING_ANONYMOUS_WITH_AGE,
                                                                       Consumed.with(stringSerde, stringSerde))
                .mapValues(v -> mapToPartner(v)) // deserialise json
                .mapValues(p -> PartnerGenerator.COUNTRY_CODES.get(p.getNationality())) // get country code
                .groupBy((k, country) -> country)
        ;

        // stick the count in a local store
        countByCountry.count(Materialized.as(COUNT_BY_PARTNER_COUNTRY_STORE_NAME));

        // stick the count in the topic used by the global ktable
        countByCountry.count().toStream().mapValues(v -> v.toString()).to(PARTNER_CREATED_GERMAN_SPEAKING_GLOBAL_COUNT);

        // build the global ktable which can be queried from any instance to get partners - is this like an event store?
        builder.globalTable(PARTNER_CREATED_ANONYMOUS, Materialized.as(GLOBAL_PARTNERS_STORE_NAME));

        // complete building...
        final KafkaStreams streams = new KafkaStreams(builder.build(), streamsConfiguration);

        // ... error handling...
        streams.setUncaughtExceptionHandler((thread, exception) -> {
            // TODO what happens to the kafka record?
            exception.printStackTrace();
            System.err.println("Caught exception on thread " + thread.getName() + ": " + exception.getMessage());
        });

        // ... shutdown hook...
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));

        // DONT CLEAN UP, rather manually delete from /tmp/kafka-streams/...
        // that way, we can start multiple instances
        //
        // streams.cleanUp();

        // ... and START!
        streams.start();

        // output the stores state when you hit enter
        Scanner scanner = new Scanner(System.in);
        while(true) {
            String query = scanner.nextLine();
            service(streams, query);
        }
    }

    private static void service(KafkaStreams streams, String query) {
        try {
            System.out.println("================================");
            long start = System.currentTimeMillis();
            if(query.trim().isEmpty()) {
                ReadOnlyKeyValueStore<String, Long> countKeyValueStore =
                        streams.store(COUNT_BY_PARTNER_COUNTRY_STORE_NAME, QueryableStoreTypes.keyValueStore());
                try (KeyValueIterator<String, Long> all = countKeyValueStore.all()) {
                    all.forEachRemaining(e -> {
                        System.out.println("\tCOUNT " + e.key + ": " + e.value);
                    });

                    System.out.printf("counted all in %s ms\n", (System.currentTimeMillis() - start));
                }

                start = System.currentTimeMillis();
                ReadOnlyKeyValueStore<String, Long> globalCountKeyValueStore =
                        streams.store(GLOBAL_COUNT_BY_PARTNER_COUNTRY_STORE_NAME, QueryableStoreTypes.keyValueStore());
                try (KeyValueIterator<String, Long> all = globalCountKeyValueStore.all()) {
                    all.forEachRemaining(e -> {
                        System.out.println("\tGLOBAL COUNT " + e.key + ": " + e.value);
                    });

                    System.out.printf("counted all global in %s ms\n", (System.currentTimeMillis() - start));
                }
            } else {
                ReadOnlyKeyValueStore<String, String> partnersStore = streams.store(GLOBAL_PARTNERS_STORE_NAME, QueryableStoreTypes.keyValueStore());
                String partner = partnersStore.get(query.trim());
                System.out.printf("PARTNER fetched in %s ms: %s\n", (System.currentTimeMillis() - start), partner);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
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