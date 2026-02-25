package com.enterprise.slackassistant.service;

@FunctionalInterface
public interface ReminderCloseCallback {
    void accept(String threadKey, String channelId, String threadTs);
}
