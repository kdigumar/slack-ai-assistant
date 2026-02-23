package com.enterprise.slackassistant.kafka;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Kafka message representing a Slack event to be processed.
 * Serialized/deserialized as JSON for the Kafka queue.
 */
public class SlackEventMessage {

    private final String eventId;
    private final String channelId;
    private final String channelName;
    private final String userId;
    private final String userMessage;
    private final String timestamp;

    @JsonCreator
    public SlackEventMessage(
            @JsonProperty("eventId") String eventId,
            @JsonProperty("channelId") String channelId,
            @JsonProperty("channelName") String channelName,
            @JsonProperty("userId") String userId,
            @JsonProperty("userMessage") String userMessage,
            @JsonProperty("timestamp") String timestamp) {
        this.eventId = eventId;
        this.channelId = channelId;
        this.channelName = channelName;
        this.userId = userId;
        this.userMessage = userMessage;
        this.timestamp = timestamp;
    }

    /**
     * Factory method for creating a new event message.
     */
    public static SlackEventMessage of(
            String eventId,
            String channelId,
            String channelName,
            String userId,
            String userMessage) {
        return new SlackEventMessage(
                eventId,
                channelId,
                channelName,
                userId,
                userMessage,
                Instant.now().toString()
        );
    }

    public String getEventId() {
        return eventId;
    }

    public String getChannelId() {
        return channelId;
    }

    public String getChannelName() {
        return channelName;
    }

    public String getUserId() {
        return userId;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public String getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "SlackEventMessage{" +
                "eventId='" + eventId + '\'' +
                ", channelId='" + channelId + '\'' +
                ", channelName='" + channelName + '\'' +
                ", userId='" + userId + '\'' +
                ", userMessage='" + userMessage + '\'' +
                ", timestamp='" + timestamp + '\'' +
                '}';
    }
}

