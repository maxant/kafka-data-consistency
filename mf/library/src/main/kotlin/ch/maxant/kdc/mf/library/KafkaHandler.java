package ch.maxant.kdc.mf.library;

import org.apache.kafka.clients.consumer.ConsumerRecord;

public interface KafkaHandler {

    /** the key used in the config which then contains all the consumer configs */
    String getKey();

    /** if true, then all records from a single poll are processed in parallel. default is false. */
    default Boolean getRunInParallel() { return false; }

    default void handle(ConsumerRecord<String, String> record) {
        // noop
    }

}
