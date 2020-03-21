package ch.maxant.kdc.library;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.Collection;

public interface RecordHandler {

    Collection<String> getSubscriptionTopics();

    void handleRecord(ConsumerRecord<String, String> record, KafkaAdapter kafkaAdapter) throws Exception;

    /** if returns true, then you can only send using transactions. otherwise
     * you can only send without transactions. default returns false */
    default boolean useTransactions() { return false; }
}
