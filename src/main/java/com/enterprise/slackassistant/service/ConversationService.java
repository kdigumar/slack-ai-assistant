package com.enterprise.slackassistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
    private static final int MAX_HISTORY = 10;
    private static final long SESSION_TIMEOUT_MINUTES = 5;
    private final Map<String, Conversation> conversations = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public ConversationService() {
        scheduler.scheduleAtFixedRate(this::cleanupStale, 1, 1, TimeUnit.MINUTES);
    }

    public void addMessage(String threadKey, String role, String content) {
        conversations.compute(threadKey, (k, conv) -> {
            if (conv == null) {
                conv = new Conversation();
            }
            conv.addMessage(role, content);
            conv.updateLastActivity();
            return conv;
        });
    }

    public List<Map<String, String>> getHistory(String threadKey) {
        Conversation conv = conversations.get(threadKey);
        return conv != null ? conv.getMessages() : List.of();
    }

    public void closeConversation(String threadKey) {
        Conversation removed = conversations.remove(threadKey);
        if (removed != null) {
            log.info("Conversation closed | threadKey='{}' | messages={}", threadKey, removed.getMessages().size());
        }
    }

    /** Remove stale conversations based on last activity time only (no content). */
    private void cleanupStale() {
        Instant now = Instant.now();
        Instant staleThreshold = now.minusSeconds(SESSION_TIMEOUT_MINUTES * 60);

        for (Map.Entry<String, Conversation> entry : conversations.entrySet()) {
            String threadKey = entry.getKey();
            Conversation conv = entry.getValue();

            if (conv.getLastActivity().isBefore(staleThreshold)) {
                conversations.remove(threadKey);
                log.info("Auto-closing stale conversation | threadKey='{}'", threadKey);
            }
        }
    }

    public String generateThreadKey(String channelId, String userId, String threadTs) {
        if (threadTs != null && !threadTs.isEmpty()) {
            return channelId + ":" + threadTs;
        }
        return channelId + ":" + userId;
    }

    private static class Conversation {
        private final List<Map<String, String>> messages = new ArrayList<>();
        private Instant lastActivity = Instant.now();

        void addMessage(String role, String content) {
            messages.add(Map.of("role", role, "content", content));
            if (messages.size() > MAX_HISTORY) {
                messages.remove(0);
            }
        }

        List<Map<String, String>> getMessages() {
            return new ArrayList<>(messages);
        }

        void updateLastActivity() {
            lastActivity = Instant.now();
        }

        Instant getLastActivity() {
            return lastActivity;
        }
    }
}
