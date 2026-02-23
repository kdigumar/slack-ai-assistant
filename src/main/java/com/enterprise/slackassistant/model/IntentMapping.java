package com.enterprise.slackassistant.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class IntentMapping {

    @JsonProperty("appId")
    private String appId;

    @JsonProperty("intentName")
    private String intentName;

    @JsonProperty("apiNames")
    private List<String> apiNames;

    public IntentMapping() {}

    public IntentMapping(String appId, String intentName, List<String> apiNames) {
        this.appId = appId;
        this.intentName = intentName;
        this.apiNames = apiNames;
    }

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }

    public String getIntentName() { return intentName; }
    public void setIntentName(String intentName) { this.intentName = intentName; }

    public List<String> getApiNames() { return apiNames; }
    public void setApiNames(List<String> apiNames) { this.apiNames = apiNames; }
}
