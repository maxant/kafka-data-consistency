package ch.maxant.kdc.mf.library;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.eclipse.microprofile.reactive.messaging.Message;

public interface KafkaHandler {
    String getTopic();

    default void handle(ConsumerRecord<String, String> record) {
        // noop
    }

    /** use {@link #handle(ConsumerRecord)} instead */
    @Deprecated(forRemoval = true)
    default void handle(Message<String> message) {
        // noop
    }
}
