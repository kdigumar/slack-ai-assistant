package com.enterprise.slackassistant.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes Slack events to Kafka for async processing.
 * Only active when kafka.enabled=true in configuration.
 */
@Service
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = false)
public class SlackEventProducer {

    private static final Logger log = LoggerFactory.getLogger(SlackEventProducer.class);

    private final KafkaTemplate<String, SlackEventMessage> kafkaTemplate;
    private final String topicName;

    public SlackEventProducer(
            KafkaTemplate<String, SlackEventMessage> kafkaTemplate,
            @Value("${kafka.topic.slack-events:slack-events}") String topicName) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicName = topicName;
        log.info("SlackEventProducer: initialized for topic='{}'", topicName);
    }

    /**
     * Sends a Slack event to Kafka for async processing.
     *
     * @param message the Slack event message
     * @return CompletableFuture that completes when the message is acknowledged
     */
    public CompletableFuture<SendResult<String, SlackEventMessage>> send(SlackEventMessage message) {
        log.info("Kafka: publishing event | eventId='{}' channelId='{}' userId='{}'",
                message.getEventId(), message.getChannelId(), message.getUserId());

        // Use channelId as the partition key for ordering within a channel
        return kafkaTemplate.send(topicName, message.getChannelId(), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Kafka: FAILED to publish event | eventId='{}' error='{}'",
                                message.getEventId(), ex.getMessage());
                    } else {
                        log.info("Kafka: PUBLISHED event | eventId='{}' partition={} offset={}",
                                message.getEventId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    /**
     * Sends a message synchronously (blocks until acknowledged).
     */
    public void sendSync(SlackEventMessage message) {
        try {
            send(message).get();
        } catch (Exception e) {
            log.error("Kafka: sync send failed | eventId='{}' error='{}'",
                    message.getEventId(), e.getMessage());
            throw new RuntimeException("Failed to publish to Kafka", e);
        }
    }
}

