package ch.maxant.kdc.partners;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Properties;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Future;

import static org.apache.kafka.clients.producer.ProducerConfig.ACKS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG;

public class ThroughputTest {

    public static void main(String[] args) throws Exception {

        ///////////////////////////////////////////////////
        //
        // generate data every 100ms and see how long it takes to go thru a stream
        //
        // requires: kafka_2.11-2.4.1/bin/kafka-topics.sh --create --zookeeper $(minikube ip):30000 --replication-factor 2 --partitions 4 --topic throughput-test-source
        //           kafka_2.11-2.4.1/bin/kafka-topics.sh --create --zookeeper $(minikube ip):30000 --replication-factor 2 --partitions 4 --topic throughput-test-aggregated
        //
        ///////////////////////////////////////////////////

        final Properties props = new Properties();
        props.put(BOOTSTRAP_SERVERS_CONFIG, "maxant.ch:30001,maxant.ch:30002");
        props.put(ACKS_CONFIG, "all");
        final KafkaProducer<String, String> producer = new KafkaProducer<>(props, new StringSerializer(), new StringSerializer());
        final ObjectMapper om = new ObjectMapper();
        int i = 0;
        UUID id = UUID.randomUUID();
        Queue<Long> lastCommitTimes = new CircularFifoQueue<>(100);
        while (true) {
            i++;
            long start = System.currentTimeMillis();
            ThroughputInitialRecord data = new ThroughputInitialRecord(id.toString(), LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), i % 10);
            String json = om.writeValueAsString(data);
            ProducerRecord<String, String> record = new ProducerRecord<>("throughput-test-source", null, id.toString(), json);
            Future<RecordMetadata> f = producer.send(record);
            RecordMetadata recordMetadata = f.get(); // <=== BLOCKS!
            long duration = System.currentTimeMillis() - start;
            if(i > 10) lastCommitTimes.add(duration);
            double avgCommitTime = lastCommitTimes.stream().mapToDouble(Long::doubleValue).average().orElse(0);
            System.out.format("%s - wrote test data to kafka in %dms, averaging %fms: %s, result: offset=%s, timestamp=%s\n",
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    duration,
                    avgCommitTime,
                    json,
                    recordMetadata.offset(),
                    new Date(recordMetadata.timestamp()).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            );
            Thread.sleep(10);
            if (i % 10 == 0) {
                id = UUID.randomUUID();
                System.out.println("started new transaction: " + id);
                Thread.sleep(3000);
            }
        }
    }

}
