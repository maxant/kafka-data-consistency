// original from here: https://raw.githubusercontent.com/confluentinc/kafka-streams-examples/5.3.0-post/src/main/java/io/confluent/examples/streams/MapFunctionLambdaExample.java
package ch.maxant.kdc.partners;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.SessionWindows;
import org.apache.kafka.streams.kstream.Suppressed;

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

        streamsConfiguration.put(StreamsConfig.BUFFERED_RECORDS_PER_PARTITION_CONFIG, 1);
        streamsConfiguration.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, 1);
        streamsConfiguration.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 1);
        streamsConfiguration.put(StreamsConfig.POLL_MS_CONFIG, 1);

        streamsConfiguration.put(ProducerConfig.LINGER_MS_CONFIG, 0); // overrides 100 for stream, even though normal producer is 0

        final Serde<String> stringSerde = Serdes.String();

        StreamsBuilder builder = new StreamsBuilder();

        KStream<String, String> throughputTestSourceStream = builder.stream("throughput-test-source", Consumed.with(stringSerde, stringSerde));

        // see https://kafka.apache.org/20/documentation/streams/developer-guide/dsl-api.html#session-windows
        throughputTestSourceStream
                .peek((k,v) -> {
                    String start = fromJson(v, ThroughputInitialRecord.class).getStart();
                    long timeFromStart = System.currentTimeMillis() - LocalDateTime.parse(start).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                    System.out.printf("%s handling record, %dms after it was created: %s\r\n", LocalDateTime.now(), timeFromStart, v);
                })
                .groupByKey()
                .windowedBy(SessionWindows.with(Duration.ofMillis(400)).grace(Duration.ofMillis(0))) // grace is important otherwise it doesnt work - see https://cwiki.apache.org/confluence/display/KAFKA/KIP-328%3A+Ability+to+suppress+updates+for+KTables: All but the final result for windows. You can use suppress get exactly one final result per window/key for windowed computations. This includes both time and session windows. This feature requires adding a "grace period" parameter for windows.
                .aggregate(
                    () -> toJson(om, new ThroughputAggregateRecord()),
                    (k, v, a) -> {
                        ThroughputAggregateRecord a1 = fromJson(a, ThroughputAggregateRecord.class);
                        a1.setId(k);
                        a1.setLastAggregation(LocalDateTime.now().toString());
                        ThroughputInitialRecord r = a1.addRecord(fromJson(v, ThroughputInitialRecord.class));

                        String start = r.getStart();
                        long timeFromStart = System.currentTimeMillis() - LocalDateTime.parse(start).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                        System.out.printf("%s aggregating record, %dms after it was created: %s\r\n", LocalDateTime.now(), timeFromStart, v);

                        return toJson(om, a1);
                    },
                    (k, a, b) -> { // always used but only useful when two previous windows are joined because of records arriving late, that join the windows
                        ThroughputAggregateRecord a1 = fromJson(a, ThroughputAggregateRecord.class);
                        ThroughputAggregateRecord b1 = fromJson(b, ThroughputAggregateRecord.class);
                        a1.merge(b1);
                        System.out.printf("%s merging %s\r\n", LocalDateTime.now(), k);
                        return toJson(om, a1);
                    })
                .suppress(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded()))
                .mapValues((k,v) -> {
                    LocalDateTime wStart = new Date(k.window().start()).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                    LocalDateTime wEnd = new Date(k.window().end()).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                    System.out.printf("%s grouped and windowed in %dms from %s to %s: key=%s, value=%s\r\n",
                            LocalDateTime.now(),
                            wStart.until(wEnd, ChronoUnit.MILLIS),
                            wStart,
                            wEnd,
                            k.key(),
                            v);
                    System.out.printf("");
                    if(v != null) { // TODO why the hell is it sometimes null?!
                        ThroughputAggregateRecord a = fromJson(v, ThroughputAggregateRecord.class);
                        a.setFinalMappingTime(LocalDateTime.now().toString());
                        a.setWindowStart(wStart.toString());
                        a.setWindowEnd(wEnd.toString());
                        v = toJson(om, a);
                    }
                    return v;
                })
                .toStream((k,v) -> k.key())
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