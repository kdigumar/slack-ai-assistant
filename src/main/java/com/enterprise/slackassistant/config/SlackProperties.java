package com.enterprise.slackassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "slack")
public class SlackProperties {

    private String botToken;
    private String signingSecret;
    private Map<String, String> channelAppMapping = new HashMap<>();

    public String getBotToken() { return botToken; }
    public void setBotToken(String botToken) { this.botToken = botToken; }

    public String getSigningSecret() { return signingSecret; }
    public void setSigningSecret(String signingSecret) { this.signingSecret = signingSecret; }

    public Map<String, String> getChannelAppMapping() { return channelAppMapping; }
    public void setChannelAppMapping(Map<String, String> channelAppMapping) { this.channelAppMapping = channelAppMapping; }
}
