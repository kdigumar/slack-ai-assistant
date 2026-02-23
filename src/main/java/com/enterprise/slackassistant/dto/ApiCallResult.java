package com.enterprise.slackassistant.dto;

import java.util.Map;

public class ApiCallResult {

    private String apiName;
    private boolean success;
    private Map<String, Object> data;
    private String errorMessage;

    public ApiCallResult() {}

    public ApiCallResult(String apiName, boolean success, Map<String, Object> data, String errorMessage) {
        this.apiName = apiName;
        this.success = success;
        this.data = data;
        this.errorMessage = errorMessage;
    }

    public String getApiName() { return apiName; }
    public void setApiName(String apiName) { this.apiName = apiName; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public static ApiCallResult success(String apiName, Map<String, Object> data) {
        return new ApiCallResult(apiName, true, data, null);
    }

    public static ApiCallResult failure(String apiName, String errorMessage) {
        return new ApiCallResult(apiName, false, Map.of(), errorMessage);
    }
}
