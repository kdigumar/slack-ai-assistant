package com.enterprise.slackassistant.config;

import com.enterprise.slackassistant.service.ConversationService;
import com.enterprise.slackassistant.service.LlmService;
import com.enterprise.slackassistant.service.MessageBufferService;
import com.enterprise.slackassistant.service.SlackService;
import com.enterprise.slackassistant.service.ThreadReminderService;
import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.methods.MethodsClient;
import com.slack.api.model.event.MessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class SlackConfig {

    private static final Logger log = LoggerFactory.getLogger(SlackConfig.class);
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    @Bean
    public AppConfig appConfig(SlackProperties slackProperties) {
        return AppConfig.builder()
                .singleTeamBotToken(slackProperties.getBotToken())
                .signingSecret(slackProperties.getSigningSecret())
                .build();
    }

    @Bean
    public MethodsClient methodsClient(AppConfig appConfig) {
        return com.slack.api.Slack.getInstance().methods(appConfig.getSingleTeamBotToken());
    }

    @Bean
    public App slackApp(AppConfig appConfig, 
                        LlmService llmService, 
                        @Lazy SlackService slackService,
                        MessageBufferService bufferService,
                        ConversationService conversationService,
                        ThreadReminderService threadReminderService) {
        
        // Setup reminder callback for ThreadReminderService (time-based only)
        threadReminderService.setReminderCallback((threadKey, channelId, threadTs) -> {
            String reminder = ":wave: Just checking in - did the solution work for you? " +
                    "Let me know if you need any more help!";
            slackService.postMessage(channelId, reminder, threadTs);
            log.info("[REMINDER] Posted to Slack | channel='{}' thread='{}'", channelId, threadTs);
        });

        threadReminderService.setCloseCallback((threadKey, channelId, threadTs) -> {
            conversationService.closeConversation(threadKey);
            slackService.postMessage(channelId,
                ":hourglass_flowing_sand: This conversation has been closed due to inactivity. " +
                "Feel free to send a new message anytime to start fresh!", threadTs);
            log.info("[CLOSURE] Session ended and Slack notified | channel='{}' thread='{}'", channelId, threadTs);
        });

        App app = new App(appConfig);

        // Handle messages
        app.event(MessageEvent.class, (payload, ctx) -> {
            MessageEvent event = payload.getEvent();

            if (event.getBotId() != null || event.getSubtype() != null) {
                return ctx.ack();
            }

            String channelId = event.getChannel();
            String userId = event.getUser();
            String userMessage = event.getText();
            String threadTs = event.getThreadTs();
            String messageTs = event.getTs();

            // For channel messages (no thread), use messageTs to create a thread reply. For thread messages, use threadTs.
            String replyThreadTs = (threadTs != null && !threadTs.isEmpty()) ? threadTs : messageTs;

            log.info("[INCOMING] User query received | channel='{}' user='{}' | threadTs='{}' messageTs='{}' | replyWillGoTo='{}'",
                    channelId, userId, threadTs, messageTs, replyThreadTs);

            String threadKey = conversationService.generateThreadKey(channelId, userId, replyThreadTs);

            // Record user message - updating user timestamp
            threadReminderService.recordUserMessage(threadKey, channelId, replyThreadTs);
            log.info("[USER ACTIVITY] Updated user timestamp | threadKey='{}' | user query thread is '{}'", threadKey, replyThreadTs);

            // Buffer the message for debouncing (preserves first message's thread for reply)
            bufferService.bufferMessage(userId, channelId, userMessage, replyThreadTs, (combinedMessage, bufferedCtx) -> {
                EXECUTOR.submit(() -> {
                    try {
                        List<Map<String, String>> history = conversationService.getHistory(threadKey);
                        conversationService.addMessage(threadKey, "user", combinedMessage);

                        log.info("[LLM] Processing message | threadKey='{}' | replyTo='{}' | history={} messages",
                                threadKey, bufferedCtx.replyThreadTs, history.size());

                        String response = llmService.chat(combinedMessage, history);
                        conversationService.addMessage(threadKey, "assistant", response);

                        // Record bot response - updating bot timestamp
                        threadReminderService.recordBotResponse(threadKey);
                        log.info("[BOT ACTIVITY] Updated bot timestamp | threadKey='{}'", threadKey);

                        // Send response to user's thread
                        log.info("[RESPONSE] Sending response to user thread | channel='{}' threadTs='{}' | {} chars",
                                bufferedCtx.channelId, bufferedCtx.replyThreadTs, response.length());
                        slackService.postMessage(bufferedCtx.channelId, response, bufferedCtx.replyThreadTs);
                        log.info("[RESPONSE] Response sent to user thread successfully | threadKey='{}'", threadKey);

                    } catch (Exception e) {
                        log.error("[ERROR] Processing failed: {}", e.getMessage(), e);
                        threadReminderService.recordBotError(threadKey); // clear processing flag on error
                        slackService.postMessage(bufferedCtx.channelId,
                            "Sorry, something went wrong. Please try again.", bufferedCtx.replyThreadTs);
                    }
                });
            });

            return ctx.ack();
        });

        return app;
    }
}
