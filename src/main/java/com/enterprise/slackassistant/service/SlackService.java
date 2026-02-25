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
    private static final int MAX_MESSAGE_LENGTH = 3900; // Slack limit is 4000, leave buffer

    private final MethodsClient methodsClient;

    public SlackService(MethodsClient methodsClient) {
        this.methodsClient = methodsClient;
    }

    public void postMessage(String channelId, String text) {
        postMessage(channelId, text, null);
    }

    public void postMessage(String channelId, String text, String threadTs) {
        try {
            // Split long messages
            if (text.length() > MAX_MESSAGE_LENGTH) {
                postLongMessage(channelId, text, threadTs);
                return;
            }

            ChatPostMessageRequest.ChatPostMessageRequestBuilder builder = 
                ChatPostMessageRequest.builder()
                    .channel(channelId)
                    .text(text)
                    .mrkdwn(true);

            // Reply in thread if threadTs is provided
            if (threadTs != null && !threadTs.isEmpty()) {
                builder.threadTs(threadTs);
            }

            ChatPostMessageResponse response = methodsClient.chatPostMessage(builder.build());
            
            if (response.isOk()) {
                log.info("[SLACK] Posted to Slack | channel='{}' threadTs='{}'", channelId, threadTs);
            } else {
                log.error("Slack error: {}", response.getError());
            }
        } catch (SlackApiException | IOException e) {
            log.error("Failed to post to Slack: {}", e.getMessage());
        }
    }

    private void postLongMessage(String channelId, String text, String threadTs) {
        int start = 0;
        int part = 1;
        
        while (start < text.length()) {
            int end = Math.min(start + MAX_MESSAGE_LENGTH, text.length());
            
            // Try to break at a newline or space
            if (end < text.length()) {
                int lastNewline = text.lastIndexOf('\n', end);
                int lastSpace = text.lastIndexOf(' ', end);
                if (lastNewline > start + 100) {
                    end = lastNewline + 1;
                } else if (lastSpace > start + 100) {
                    end = lastSpace + 1;
                }
            }

            String chunk = text.substring(start, end);
            log.info("Posting part {} ({} chars)", part, chunk.length());
            
            try {
                ChatPostMessageRequest.ChatPostMessageRequestBuilder builder = 
                    ChatPostMessageRequest.builder()
                        .channel(channelId)
                        .text(chunk)
                        .mrkdwn(true);

                if (threadTs != null && !threadTs.isEmpty()) {
                    builder.threadTs(threadTs);
                }

                methodsClient.chatPostMessage(builder.build());
            } catch (Exception e) {
                log.error("Failed to post part {}: {}", part, e.getMessage());
            }

            start = end;
            part++;
        }
    }
}
