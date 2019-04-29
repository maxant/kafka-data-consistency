package ch.maxant.kdc.library;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.Collection;

public interface RecordHandler {

    Collection<String> getTopics();

    void handleRecord(ConsumerRecord<String, String> record, KafkaAdapter kafkaAdapter) throws Exception;

}
