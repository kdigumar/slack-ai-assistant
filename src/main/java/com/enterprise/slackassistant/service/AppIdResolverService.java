package com.enterprise.slackassistant.service;

import com.enterprise.slackassistant.config.SlackProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AppIdResolverService {

    private static final Logger log = LoggerFactory.getLogger(AppIdResolverService.class);

    private final SlackProperties slackProperties;

    public AppIdResolverService(SlackProperties slackProperties) {
        this.slackProperties = slackProperties;
    }

    public String resolveAppId(String channelName) {
        String appId = slackProperties.getChannelAppMapping().get(channelName);
        if (appId == null) {
            log.warn("         ⚠ No appId mapping for channel='{}'. Known mappings: {}", channelName,
                    slackProperties.getChannelAppMapping().keySet());
            throw new IllegalArgumentException(
                    "Unknown Slack channel '" + channelName + "'. Add it to slack.channel-app-mapping in application.yml");
        }
        log.info("         AppIdResolver: channel='{}' → appId='{}'", channelName, appId);
        return appId;
    }
}
