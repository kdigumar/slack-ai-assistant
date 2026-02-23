package com.enterprise.slackassistant.dto;

import java.util.Map;

public class IntentResult {

    private String intentName;
    private Map<String, String> parameters;

    public IntentResult() {}

    public IntentResult(String intentName, Map<String, String> parameters) {
        this.intentName = intentName;
        this.parameters = parameters;
    }

    public String getIntentName() { return intentName; }
    public void setIntentName(String intentName) { this.intentName = intentName; }

    public Map<String, String> getParameters() { return parameters; }
    public void setParameters(Map<String, String> parameters) { this.parameters = parameters; }

    public static IntentResult of(String intentName, Map<String, String> parameters) {
        return new IntentResult(intentName, parameters);
    }
}
