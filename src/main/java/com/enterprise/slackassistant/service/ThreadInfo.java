package com.enterprise.slackassistant.service;

import java.time.Instant;

/**
 * Represents metadata for a Slack thread without storing conversation messages.
 * Tracks only user/bot activity timestamps for time-based reminder and closure.
 */
public class ThreadInfo {
    private final String sessionId;
    private Instant lastUserTime;
    private Instant lastBotTime;
    private int reminderCount;
    private String channelId;
    private String threadTs;
    private boolean botProcessing = false; // true while LLM is working; skip scheduler checks

    public ThreadInfo(String sessionId) {
        this.sessionId = sessionId;
        this.lastUserTime = Instant.now();
        this.lastBotTime = null;
        this.reminderCount = 0;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Instant getLastUserTime() {
        return lastUserTime;
    }

    public void setLastUserTime(Instant lastUserTime) {
        this.lastUserTime = lastUserTime;
    }

    public Instant getLastBotTime() {
        return lastBotTime;
    }

    public void setLastBotTime(Instant lastBotTime) {
        this.lastBotTime = lastBotTime;
    }

    public int getReminderCount() {
        return reminderCount;
    }

    public void incrementReminderCount() {
        this.reminderCount++;
    }

    public boolean isBotProcessing() {
        return botProcessing;
    }

    public void setBotProcessing(boolean botProcessing) {
        this.botProcessing = botProcessing;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public String getThreadTs() {
        return threadTs;
    }

    public void setThreadTs(String threadTs) {
        this.threadTs = threadTs;
    }

    @Override
    public String toString() {
        return "ThreadInfo{" +
                "sessionId='" + sessionId + '\'' +
                ", lastUserTime=" + lastUserTime +
                ", lastBotTime=" + lastBotTime +
                ", reminderCount=" + reminderCount +
                '}';
    }
}
