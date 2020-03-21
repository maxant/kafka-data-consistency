// original from here: https://raw.githubusercontent.com/confluentinc/kafka-streams-examples/5.3.0-post/src/main/java/io/confluent/examples/streams/MapFunctionLambdaExample.java
package ch.maxant.kdc.partners;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.SessionWindows;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Properties;

/**
 * groups everything with the same TX id using a window.
 */
public class ThroughputTestStream {

    private static final ObjectMapper om = new ObjectMapper();

    public static void main(final String[] args) {
        final String bootstrapServers = args.length > 0 ? args[0] : "maxant.ch:30001,maxant.ch:30002";
        final Properties streamsConfiguration = new Properties();
        streamsConfiguration.put(StreamsConfig.APPLICATION_ID_CONFIG, "throughput-test");
        streamsConfiguration.put(StreamsConfig.CLIENT_ID_CONFIG, "throughput-test-client");
        streamsConfiguration.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        streamsConfiguration.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        streamsConfiguration.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        streamsConfiguration.put(StreamsConfig.STATE_DIR_CONFIG, "/tmp/kafka-streams/throughput-test/" + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).replace("-", "").replace(":", ""));

        final Serde<String> stringSerde = Serdes.String();

        StreamsBuilder builder = new StreamsBuilder();

        KStream<String, String> throughputTestSourceStream = builder.stream("throughput-test-source", Consumed.with(stringSerde, stringSerde));

        throughputTestSourceStream
                .peek((k,v) -> System.out.printf("%s handling record: %s\r\n", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), v))
                .groupBy((k,v) -> k)
                .windowedBy(SessionWindows.with(Duration.of(2000, ChronoUnit.SECONDS))) // time has to be greater than the one second it takes to publish the next record in the TX
                .aggregate(
                    () -> toJson(om, new ThroughputAggregateRecord()),
                    (k, v, a) -> {
                        ThroughputAggregateRecord a1 = fromJson(a, ThroughputAggregateRecord.class);
                        a1.setId(k);
                        a1.addRecord(fromJson(v, ThroughputInitialRecord.class));
                        return toJson(om, a1);
                    },
                    (k, a, b) -> {
                        return b;
                    })
//                .mapValues(v -> toJson(om, v))
                .toStream()
                .peek((k,v) -> {
                    LocalDateTime wStart = new Date(k.window().start()).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                    LocalDateTime wEnd = new Date(k.window().end()).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                    System.out.printf("grouped and windowed from %s to %s: %s", wStart, wEnd, v);
                })
                .map((k, v) -> new KeyValue<>(k.key(), v))
                .to("throughput-test-aggregated");

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
    }

    static <T> T fromJson(String s, Class<T> clazz) {
        return fromJson(om, s, clazz);
    }

    static <T> T fromJson(ObjectMapper om, String s, Class<T> clazz) {
        try {
            return om.readValue(s, clazz);
        }catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static String toJson(ObjectMapper om, Object o) {
        try {
            return om.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}