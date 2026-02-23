package com.enterprise.slackassistant.service;

import com.enterprise.slackassistant.dto.ApiCallResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ArtemisApiService {

    private static final Logger log = LoggerFactory.getLogger(ArtemisApiService.class);

    private final long mockDelayMs;

    public ArtemisApiService(@Value("${artemis.mock-delay-ms:50}") long mockDelayMs) {
        this.mockDelayMs = mockDelayMs;
    }

    public List<ApiCallResult> callAll(List<String> apiNames, Map<String, String> parameters) {
        log.info("         ArtemisAPI: calling {} API(s) with params={}", apiNames.size(), parameters);
        List<ApiCallResult> results = new ArrayList<>();
        for (String apiName : apiNames) {
            log.info("         ArtemisAPI: → calling '{}' ...", apiName);
            simulateLatency();
            ApiCallResult result = dispatch(apiName, parameters);
            log.info("         ArtemisAPI: ← '{}' success={} data={}", apiName, result.isSuccess(),
                    result.isSuccess() ? result.getData() : result.getErrorMessage());
            results.add(result);
        }
        return results;
    }

    private ApiCallResult dispatch(String apiName, Map<String, String> params) {
        return switch (apiName.toLowerCase()) {
            case "user"         -> callUserApi(params);
            case "business"     -> callBusinessApi(params);
            case "ticketcreate" -> callTicketCreateApi(params);
            case "viewticket"   -> callViewTicketApi(params);
            case "updateuser"   -> callUpdateUserApi(params);
            case "appcrash"     -> callAppCrashApi(params);
            default -> ApiCallResult.failure(apiName, "Unknown API: '" + apiName + "'");
        };
    }

    private ApiCallResult callUserApi(Map<String, String> params) {
        String userId = params.getOrDefault("userId", "USR-00123");
        return ApiCallResult.success("user", Map.of(
                "userId", userId,
                "displayName", "Jane Smith",
                "email", "jane.smith@example.com",
                "status", "INACTIVE",
                "lastLogin", "2024-11-10T08:34:00Z",
                "roles", List.of("VIEWER"),
                "accountNotes", "Auto-deactivated after 90 days of inactivity."
        ));
    }

    private ApiCallResult callBusinessApi(Map<String, String> params) {
        String userId = params.getOrDefault("userId", "USR-00123");
        return ApiCallResult.success("business", Map.of(
                "userId", userId,
                "assignedBusinesses", List.of(),
                "missingRole", "BUSINESS_VIEWER",
                "message", "User has no BUSINESS_VIEWER or BUSINESS_ADMIN role assigned."
        ));
    }

    private ApiCallResult callTicketCreateApi(Map<String, String> params) {
        String ticketId = "TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return ApiCallResult.success("ticketcreate", Map.of(
                "ticketId", ticketId,
                "title", params.getOrDefault("title", "Support Request"),
                "priority", params.getOrDefault("priority", "MEDIUM"),
                "status", "OPEN",
                "assignedTo", "on-call-queue",
                "createdAt", java.time.Instant.now().toString()
        ));
    }

    private ApiCallResult callViewTicketApi(Map<String, String> params) {
        String ticketId = params.getOrDefault("ticketId", "TKT-UNKNOWN");
        return ApiCallResult.success("viewticket", Map.of(
                "ticketId", ticketId,
                "title", "Unable to Access Artemis",
                "status", "IN_PROGRESS",
                "priority", "HIGH",
                "assignedTo", "L2-Support",
                "createdAt", "2025-02-15T10:00:00Z",
                "comments", List.of(
                        "L1: Escalated to L2 — account status issue.",
                        "L2: Investigating LDAP sync problem.")
        ));
    }

    private ApiCallResult callUpdateUserApi(Map<String, String> params) {
        String userId = params.getOrDefault("userId", "USR-00123");
        String newStatus = params.getOrDefault("status", "ACTIVE");
        return ApiCallResult.success("updateuser", Map.of(
                "userId", userId,
                "previousStatus", "INACTIVE",
                "newStatus", newStatus,
                "updatedAt", java.time.Instant.now().toString(),
                "notificationSent", true
        ));
    }

    private ApiCallResult callAppCrashApi(Map<String, String> params) {
        return ApiCallResult.success("appcrash", Map.of(
                "recentCrashes", List.of(
                        Map.of("crashId", "CRS-0091", "module", "AuthModule",
                                "timestamp", "2025-02-19T23:45:00Z",
                                "summary", "NullPointerException in TokenRefreshHandler",
                                "affectedUsers", 14),
                        Map.of("crashId", "CRS-0090", "module", "ReportingModule",
                                "timestamp", "2025-02-19T18:12:00Z",
                                "summary", "Connection timeout to reporting-db",
                                "affectedUsers", 3)),
                "totalCrashesLast24h", 2,
                "escalationChannel", "#artemis-platform"
        ));
    }

    private void simulateLatency() {
        if (mockDelayMs > 0) {
            try { Thread.sleep(mockDelayMs); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }
}
