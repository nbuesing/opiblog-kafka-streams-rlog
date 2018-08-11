package com.objectpartners.buesing.springbootapp.consumer;

import com.objectpartners.buesing.avro.CommitLog;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class CommitLogConsumer implements ConsumerSeekAware {

    @KafkaListener(topics = "commit.log")
    public void receive(ConsumerRecord<String, CommitLog> consumerRecord) {
        log.debug("topic=commit.log, offset={}, key={}, value={}", consumerRecord.offset(), consumerRecord.key(), consumerRecord.value());
    }

    @Override
    public void registerSeekCallback(final ConsumerSeekCallback callback) {
        log.debug("callback={}", callback);
    }

    @Override
    public void onPartitionsAssigned(final Map<TopicPartition, Long> assignments, final ConsumerSeekCallback callback) {
        log.debug("assignments={}, callback={}", assignments, callback);
    }

    @Override
    public void onIdleContainer(final Map<TopicPartition, Long> assignments, final ConsumerSeekCallback callback) {
        log.debug("assignments={}, callback={}", assignments, callback);
    }
}