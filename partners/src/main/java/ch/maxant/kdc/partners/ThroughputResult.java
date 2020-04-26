package ch.maxant.kdc.partners;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import static ch.maxant.kdc.partners.ThroughputTestStream.SYNTHETIC;
import static ch.maxant.kdc.partners.ThroughputTestStream.fromJson;
import static java.util.Arrays.asList;
import static org.apache.kafka.clients.consumer.ConsumerConfig.*;

public class ThroughputResult {

    public static void main(String[] args) {

        ///////////////////////////////////////////////////
        //
        // listen to data on output topic
        //
        // see https://stackoverflow.com/q/60822669/458370
        //
        ///////////////////////////////////////////////////

        final Properties props = new Properties();
        props.put(BOOTSTRAP_SERVERS_CONFIG, "maxant.ch:30001,maxant.ch:30002");
        props.put(CLIENT_ID_CONFIG, "throughput-test-result-consumer");
        props.put(GROUP_ID_CONFIG, "throughput-test-result-group-id");
        final KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer());
        consumer.subscribe(asList("throughput-test-aggregated", "throughput-test-source"));
        final ObjectMapper om = new ObjectMapper();

        Queue<Long> lastSourceProcessTimes = new CircularFifoQueue<>(20);
        Queue<Long> lastAggregateProcessTimes = new CircularFifoQueue<>(20);

        AtomicInteger i = new AtomicInteger();
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.of(10, ChronoUnit.SECONDS));
            records.iterator().forEachRemaining(r -> {
                if("throughput-test-source".equals(r.topic())) {
/*                    if(r.value() != null && !SYNTHETIC.equals(r.value())) {
                        long duration = System.currentTimeMillis() - r.timestamp();
                        if(i.incrementAndGet() > 10) lastSourceProcessTimes.add(duration);
                        System.out.printf("%s received source record, processed in %d, avg %f: %s\r\n", LocalDateTime.now(), duration,
                                lastSourceProcessTimes.stream().mapToDouble(Long::doubleValue).average().orElse(0), r);
                    }
*/
                } else if(r.value() != null) {
                    ThroughputAggregateRecord record = fromJson(om, r.value(), ThroughputAggregateRecord.class);
                    if(SYNTHETIC.equals(record.getTxId())) {
                        System.out.println("SHOULDNT GET SYNTHETIC EVENTS HERE!");
                    } else if(record.getNumRecordsProcessed() != 10) {
                        System.out.printf("%s does not contain ten records! %s\r\n", LocalDateTime.now(), r);
                    } else {
                        long now = System.currentTimeMillis();
                        String firstStart = record.getRecords().stream()
                                .sorted(Comparator.comparing(ThroughputInitialRecord::getIndexInSet))
                                .findFirst()
                                .get()
                                .getStart();
                        String lastStart = record.getRecords().stream()
                                .sorted(Comparator.comparing(ThroughputInitialRecord::getIndexInSet).reversed())
                                .findFirst()
                                .get()
                                .getStart();
                        long timeFromStart = now - LocalDateTime.parse(firstStart).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                        long timeFromEnd = now - LocalDateTime.parse(lastStart).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                        if(i.get() > 10) lastAggregateProcessTimes.add(timeFromEnd);
                        System.out.printf("%s aggregate was ok for key %s, processed in %d from start, and %d from end, avg: %f\r\n",
                                LocalDateTime.now(),
                                r.key(),
                                timeFromStart,
                                timeFromEnd,
                                lastAggregateProcessTimes.stream().mapToDouble(Long::doubleValue).average().orElse(0)
                        );
                    }
                } else {
                    System.err.printf("%s got null body for record %s\r\n", LocalDateTime.now(), r);
                }
            });
        }
    }

}
