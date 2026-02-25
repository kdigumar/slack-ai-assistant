package com.enterprise.slackassistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

@Service
public class MessageBufferService {

    private static final Logger log = LoggerFactory.getLogger(MessageBufferService.class);
    private static final long DEBOUNCE_MS = 1000;

    private final Map<String, BufferedMessages> buffers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    /**
     * @param replyThreadTs thread to reply to (threadTs if in thread, else messageTs for channel)
     */
    public void bufferMessage(String userId, String channelId, String message, String replyThreadTs,
                              BiConsumer<String, BufferedContext> onReady) {
        String key = channelId + ":" + userId;

        buffers.compute(key, (k, existing) -> {
            if (existing != null) {
                existing.cancel();
                existing.addMessage(message);
                existing.setCallback(onReady);
                log.info("[BUFFER] Buffering message | user='{}' | total={} | replyThreadTs='{}' (unchanged)",
                        userId, existing.getMessages().size(), existing.getReplyThreadTs());
            } else {
                existing = new BufferedMessages(channelId, replyThreadTs);
                existing.addMessage(message);
                existing.setCallback(onReady);
                log.info("[BUFFER] New buffer | user='{}' | replyThreadTs='{}'", userId, replyThreadTs);
            }

            final BufferedMessages buffer = existing;
            ScheduledFuture<?> future = scheduler.schedule(() -> {
                buffers.remove(key);
                String combined = String.join(" ", buffer.getMessages());
                log.info("[BUFFER] Debounce complete | user='{}' | combined='{}' ({} msgs) | replyTo='{}'",
                        userId, combined, buffer.getMessages().size(), buffer.getReplyThreadTs());
                buffer.getCallback().accept(combined, buffer.getContext());
            }, DEBOUNCE_MS, TimeUnit.MILLISECONDS);

            existing.setFuture(future);
            return existing;
        });
    }

    public static class BufferedContext {
        public final String channelId;
        public final String replyThreadTs;

        public BufferedContext(String channelId, String replyThreadTs) {
            this.channelId = channelId;
            this.replyThreadTs = replyThreadTs;
        }
    }

    private static class BufferedMessages {
        private final List<String> messages = new ArrayList<>();
        private final String channelId;
        private final String replyThreadTs;
        private ScheduledFuture<?> future;
        private BiConsumer<String, BufferedContext> callback;

        BufferedMessages(String channelId, String replyThreadTs) {
            this.channelId = channelId;
            this.replyThreadTs = replyThreadTs;
        }

        void addMessage(String msg) {
            messages.add(msg);
        }

        List<String> getMessages() {
            return messages;
        }

        String getReplyThreadTs() {
            return replyThreadTs;
        }

        BufferedContext getContext() {
            return new BufferedContext(channelId, replyThreadTs);
        }

        void setFuture(ScheduledFuture<?> future) {
            this.future = future;
        }

        void cancel() {
            if (future != null && !future.isDone()) {
                future.cancel(false);
            }
        }

        void setCallback(BiConsumer<String, BufferedContext> callback) {
            this.callback = callback;
        }

        BiConsumer<String, BufferedContext> getCallback() {
            return callback;
        }
    }
}
