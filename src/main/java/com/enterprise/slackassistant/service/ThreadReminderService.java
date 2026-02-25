package com.enterprise.slackassistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service that manages Slack thread reminders and closures based purely on user/bot activity timestamps.
 * No message content is read - only timestamps are tracked.
 */
@Service
public class ThreadReminderService {

    private static final Logger log = LoggerFactory.getLogger(ThreadReminderService.class);

    @Value("${slack.reminder-threshold-minutes:1}")
    private long reminderThresholdMinutes;

    @Value("${slack.closure-threshold-minutes:2}")
    private long closureThresholdMinutes;

    private final Map<String, ThreadInfo> threads = new ConcurrentHashMap<>();

    private ReminderCloseCallback reminderCallback;
    private ReminderCloseCallback closeCallback;

    public void setReminderCallback(ReminderCloseCallback callback) {
        this.reminderCallback = callback;
    }

    public void setCloseCallback(ReminderCloseCallback callback) {
        this.closeCallback = callback;
    }

    /**
     * Record that a user sent a message. Tracks timestamp only (no content).
     * @param threadKey threadTs if in a thread, or "channelId:userId" for channel messages (never null)
     */
    public void recordUserMessage(String threadKey, String channelId, String threadTs) {
        if (threadKey == null || threadKey.isBlank()) {
            log.warn("recordUserMessage called with null/blank key - skipping");
            return;
        }
        threads.compute(threadKey, (key, existing) -> {
            if (existing == null) {
                existing = new ThreadInfo(UUID.randomUUID().toString());
                log.info("───────────────────────────────────────────────────────────");
                log.info("[THREAD LIFECYCLE] New thread created | threadKey={} | sessionId={}", key, existing.getSessionId());
            }
            existing.setLastUserTime(Instant.now());
            existing.setChannelId(channelId);
            existing.setThreadTs(threadTs);
            existing.setBotProcessing(true); // LLM is about to process
            log.info("[USER ACTIVITY] User message received | threadKey={} | lastUserTime={} | botProcessing=true", key, existing.getLastUserTime());
            return existing;
        });
    }

    /**
     * Record that the bot sent a response for a thread.
     */
    public void recordBotResponse(String threadKey) {
        ThreadInfo info = threads.get(threadKey);
        if (info != null) {
            info.setLastBotTime(Instant.now());
            info.setBotProcessing(false); // LLM done
            log.info("[BOT ACTIVITY] Bot response sent | threadKey={} | lastBotTime={} | botProcessing=false", threadKey, info.getLastBotTime());
        } else {
            log.warn("[BOT ACTIVITY] No thread found for bot response | threadKey={}", threadKey);
        }
    }

    /**
     * Mark bot processing as done without setting lastBotTime (e.g. on error).
     */
    public void recordBotError(String threadKey) {
        ThreadInfo info = threads.get(threadKey);
        if (info != null) {
            info.setBotProcessing(false);
            log.warn("[BOT ACTIVITY] Bot error - clearing processing flag | threadKey={}", threadKey);
        }
    }

    /**
     * Scheduler that runs every 5 minutes to check thread inactivity and trigger actions.
     * Checks reminder and closure rules for each tracked thread.
     */
    @Scheduled(fixedRate = 3000) // every 1 minute
    public void checkThreadsScheduled() {
        log.info("───────────────────────────────────────────────────────────");
        log.info("[SCHEDULER] Running | Active threads: {} | Reminder threshold: {} min | Closure threshold: {} min",
                threads.size(), reminderThresholdMinutes, closureThresholdMinutes);

        Instant now = Instant.now();

        for (Map.Entry<String, ThreadInfo> entry : threads.entrySet()) {
            String threadKey = entry.getKey();
            ThreadInfo threadInfo = entry.getValue();

            long inactivityMinutes = ChronoUnit.MINUTES.between(threadInfo.getLastUserTime(), now);

            log.info("[SCHEDULER] Checking thread | threadKey={}", threadKey);
            log.info("[SCHEDULER]   lastUserTime    = {}", threadInfo.getLastUserTime());
            log.info("[SCHEDULER]   lastBotTime     = {}", threadInfo.getLastBotTime() != null ? threadInfo.getLastBotTime() : "NOT SET (bot never responded)");
            log.info("[SCHEDULER]   inactivityMins  = {} (user inactive for {} min, threshold reminder={} closure={})",
                    inactivityMinutes, inactivityMinutes, reminderThresholdMinutes, closureThresholdMinutes);
            log.info("[SCHEDULER]   reminderCount   = {}", threadInfo.getReminderCount());
            log.info("[SCHEDULER]   botProcessing   = {}", threadInfo.isBotProcessing());

            // Skip entirely while LLM is still processing - avoids premature closure
            if (threadInfo.isBotProcessing()) {
                log.info("[SCHEDULER] Skipping thread - LLM still processing | threadKey={}", threadKey);
                continue;
            }

            // Close rule: inactivity >= threshold
            if (inactivityMinutes >= closureThresholdMinutes) {
                log.info("[CLOSURE] Closing thread | threadKey={} | inactivity={}min >= {}min threshold",
                        threadKey, inactivityMinutes, closureThresholdMinutes);
                closeThread(threadKey);
                continue;
            }

            // Reminder rule: bot responded (lastBotTime set), bot was last to respond, inactivity >= threshold, only once
            boolean botResponded     = threadInfo.getLastBotTime() != null;
            boolean botWasLast       = botResponded && threadInfo.getLastBotTime().isAfter(threadInfo.getLastUserTime());
            boolean inactivityMet    = inactivityMinutes >= reminderThresholdMinutes;
            boolean notYetReminded   = threadInfo.getReminderCount() == 0;

            log.info("[SCHEDULER]   [REMINDER CHECK] botResponded={} | botWasLast={} | inactivityMet={} ({}>={}min) | notYetReminded={}",
                    botResponded, botWasLast, inactivityMet, inactivityMinutes, reminderThresholdMinutes, notYetReminded);

            if (botResponded && botWasLast && inactivityMet && notYetReminded) {
                log.info("[REMINDER] All conditions met - sending reminder (only once) | threadKey={}", threadKey);
                sendReminder(threadKey);
            } else if (threadInfo.getReminderCount() > 0) {
                log.info("[REMINDER] Already sent once | threadKey={} | reminderCount={} | Skipping", threadKey, threadInfo.getReminderCount());
            } else if (!botResponded) {
                log.info("[SCHEDULER] No action | threadKey={} | Bot has not responded yet", threadKey);
            } else if (!botWasLast) {
                log.info("[SCHEDULER] No action | threadKey={} | User replied after bot - awaiting next bot response", threadKey);
            } else {
                log.info("[SCHEDULER] No action | threadKey={} | Inactivity={}min < {}min threshold", threadKey, inactivityMinutes, reminderThresholdMinutes);
            }
        }

        log.info("[SCHEDULER] Completed | Remaining threads: {}", threads.size());
        log.info("───────────────────────────────────────────────────────────");
    }

    /**
     * Send reminder for a thread. Called by scheduler when reminder rule is met.
     * Sends only once per thread (reminderCount is incremented).
     */
    public void sendReminder(String threadKey) {
        ThreadInfo info = threads.get(threadKey);
        if (info != null && reminderCallback != null && info.getChannelId() != null) {
            try {
                reminderCallback.accept(threadKey, info.getChannelId(), info.getThreadTs());
                info.incrementReminderCount();
            } catch (Exception e) {
                log.error("[REMINDER] Error sending reminder for thread {}: {}", threadKey, e.getMessage());
            }
        }
    }

    /**
     * Get thread info for monitoring/debugging.
     */
    public ThreadInfo getThreadInfo(String threadTs) {
        return threads.get(threadTs);
    }

    /**
     * Get all active threads count.
     */
    public int getActiveThreadCount() {
        return threads.size();
    }

    /**
     * Close a thread and remove from map. Invokes close callback if set.
     * Called by scheduler when closure rule is met, or manually for testing.
     */
    public void closeThread(String threadKey) {
        ThreadInfo removed = threads.remove(threadKey);
        if (removed != null) {
            if (closeCallback != null && removed.getChannelId() != null) {
                try {
                    closeCallback.accept(threadKey, removed.getChannelId(), removed.getThreadTs());
                } catch (Exception e) {
                    log.error("[CLOSURE] Error executing close callback for thread {}: {}", threadKey, e.getMessage());
                }
            }
            log.info("[CLOSURE] Thread closed | threadKey={} | sessionId={}", threadKey, removed.getSessionId());
        }
    }
}
