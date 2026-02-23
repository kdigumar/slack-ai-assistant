package com.enterprise.slackassistant.exception;

/**
 * Thrown when no entry in {@code intent-mapping.json} matches the
 * detected intent for the given appId.
 */
public class IntentNotFoundException extends RuntimeException {

    private final String appId;
    private final String intentName;

    public IntentNotFoundException(String appId, String intentName) {
        super(String.format(
                "No intent mapping found for appId='%s' and intentName='%s'. " +
                "Please add a corresponding entry to intent-mapping.json.",
                appId, intentName));
        this.appId = appId;
        this.intentName = intentName;
    }

    public String getAppId() {
        return appId;
    }

    public String getIntentName() {
        return intentName;
    }
}





