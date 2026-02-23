package com.enterprise.slackassistant.service;

import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class SlackService {

    private static final Logger log = LoggerFactory.getLogger(SlackService.class);

    private final MethodsClient methodsClient;

    public SlackService(MethodsClient methodsClient) {
        this.methodsClient = methodsClient;
    }

    public void postMessage(String channelId, String text) {
        try {
            ChatPostMessageResponse response = methodsClient.chatPostMessage(
                    ChatPostMessageRequest.builder()
                            .channel(channelId)
                            .text(text)
                            .mrkdwn(true)
                            .build()
            );
            if (response.isOk()) {
                log.info("         SlackService: ✓ posted to channel='{}' ts='{}'", channelId, response.getTs());
            } else {
                log.error("         SlackService: ✗ Slack error posting to '{}': {}", channelId, response.getError());
            }
        } catch (SlackApiException | IOException e) {
            log.error("Failed to post to Slack channel='{}': {}", channelId, e.getMessage(), e);
        }
    }

    public void postErrorFallback(String channelId) {
        postMessage(channelId,
                ":warning: I encountered an issue processing your request. "
                + "Please try rephrasing, or contact your Artemis administrator.");
    }
}
