package com.enterprise.slackassistant.service;

import com.enterprise.slackassistant.config.ProductConfig;
import com.enterprise.slackassistant.config.ProductConfig.ProductDefinition;
import com.enterprise.slackassistant.dto.ApiCallResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Multi-product API service that routes API calls to the appropriate product handler.
 * Each product can have its own set of mock APIs.
 * Protected by Resilience4j circuit breaker for fault tolerance.
 */
@Service
public class ProductApiService {

    private static final Logger log = LoggerFactory.getLogger(ProductApiService.class);

    private final ProductConfig productConfig;

    public ProductApiService(ProductConfig productConfig) {
        this.productConfig = productConfig;
    }

    /**
     * Calls all APIs for a given product.
     * Protected by circuit breaker and retry mechanisms.
     *
     * @param productId  the product ID (e.g., "artemis", "b360", "velocity")
     * @param apiNames   list of API names to call
     * @param parameters extracted parameters from the user query
     * @return list of API call results
     */
    @CircuitBreaker(name = "external-api", fallbackMethod = "callAllFallback")
    @Retry(name = "external-api")
    public List<ApiCallResult> callAll(String productId, List<String> apiNames, Map<String, String> parameters) {
        ProductDefinition definition = productConfig.getDefinitions().get(productId);
        long mockDelayMs = definition != null ? definition.getMockDelayMs() : 50;

        log.info("         ProductAPI[{}]: calling {} API(s) with params={}", productId, apiNames.size(), parameters);

        List<ApiCallResult> results = new ArrayList<>();
        for (String apiName : apiNames) {
            log.info("         ProductAPI[{}]: → calling '{}' ...", productId, apiName);
            simulateLatency(mockDelayMs);
            ApiCallResult result = dispatch(productId, apiName, parameters);
            log.info("         ProductAPI[{}]: ← '{}' success={} data={}", productId, apiName, result.isSuccess(),
                    result.isSuccess() ? result.getData() : result.getErrorMessage());
            results.add(result);
        }
        return results;
    }

    private ApiCallResult dispatch(String productId, String apiName, Map<String, String> params) {
        return switch (productId.toLowerCase()) {
            case "artemis" -> dispatchArtemis(apiName, params);
            case "b360" -> dispatchB360(apiName, params);
            case "velocity" -> dispatchVelocity(apiName, params);
            default -> ApiCallResult.failure(apiName, "Unknown product: '" + productId + "'");
        };
    }

    // ─── ARTEMIS APIs ──────────────────────────────────────────────────────────

    private ApiCallResult dispatchArtemis(String apiName, Map<String, String> params) {
        return switch (apiName.toLowerCase()) {
            case "user" -> callArtemisUserApi(params);
            case "business" -> callArtemisBusinessApi(params);
            case "ticketcreate" -> callArtemisTicketCreateApi(params);
            case "viewticket" -> callArtemisViewTicketApi(params);
            case "updateuser" -> callArtemisUpdateUserApi(params);
            case "appcrash" -> callArtemisAppCrashApi(params);
            default -> ApiCallResult.failure(apiName, "Unknown Artemis API: '" + apiName + "'");
        };
    }

    private ApiCallResult callArtemisUserApi(Map<String, String> params) {
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

    private ApiCallResult callArtemisBusinessApi(Map<String, String> params) {
        String userId = params.getOrDefault("userId", "USR-00123");
        return ApiCallResult.success("business", Map.of(
                "userId", userId,
                "assignedBusinesses", List.of(),
                "missingRole", "BUSINESS_VIEWER",
                "message", "User has no BUSINESS_VIEWER or BUSINESS_ADMIN role assigned."
        ));
    }

    private ApiCallResult callArtemisTicketCreateApi(Map<String, String> params) {
        String ticketId = "TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return ApiCallResult.success("ticketcreate", Map.of(
                "ticketId", ticketId,
                "title", params.getOrDefault("title", "Support Request"),
                "priority", params.getOrDefault("priority", "MEDIUM"),
                "status", "OPEN",
                "assignedTo", "on-call-queue",
                "createdAt", Instant.now().toString()
        ));
    }

    private ApiCallResult callArtemisViewTicketApi(Map<String, String> params) {
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

    private ApiCallResult callArtemisUpdateUserApi(Map<String, String> params) {
        String userId = params.getOrDefault("userId", "USR-00123");
        String newStatus = params.getOrDefault("status", "ACTIVE");
        return ApiCallResult.success("updateuser", Map.of(
                "userId", userId,
                "previousStatus", "INACTIVE",
                "newStatus", newStatus,
                "updatedAt", Instant.now().toString(),
                "notificationSent", true
        ));
    }

    private ApiCallResult callArtemisAppCrashApi(Map<String, String> params) {
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

    // ─── B360 APIs ─────────────────────────────────────────────────────────────

    private ApiCallResult dispatchB360(String apiName, Map<String, String> params) {
        return switch (apiName.toLowerCase()) {
            case "auth" -> callB360AuthApi(params);
            case "report" -> callB360ReportApi(params);
            case "sync" -> callB360SyncApi(params);
            case "dashboard" -> callB360DashboardApi(params);
            case "permissions" -> callB360PermissionsApi(params);
            case "ticketcreate" -> callB360TicketCreateApi(params);
            default -> ApiCallResult.failure(apiName, "Unknown B360 API: '" + apiName + "'");
        };
    }

    private ApiCallResult callB360AuthApi(Map<String, String> params) {
        String userId = params.getOrDefault("userId", "USR-B360-001");
        return ApiCallResult.success("auth", Map.of(
                "userId", userId,
                "authStatus", "FAILED",
                "reason", "Password expired - last changed 95 days ago",
                "failedAttempts", 3,
                "lockoutStatus", "NOT_LOCKED",
                "ssoEnabled", true,
                "lastSuccessfulLogin", "2025-02-01T14:30:00Z"
        ));
    }

    private ApiCallResult callB360ReportApi(Map<String, String> params) {
        String reportId = params.getOrDefault("reportId", "RPT-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        return ApiCallResult.success("report", Map.of(
                "reportId", reportId,
                "status", "FAILED",
                "error", "Data source 'sales_db' connection timeout after 30s",
                "rowCount", 0,
                "startTime", "2025-02-23T09:00:00Z",
                "duration", "30s",
                "retryable", true
        ));
    }

    private ApiCallResult callB360SyncApi(Map<String, String> params) {
        return ApiCallResult.success("sync", Map.of(
                "lastSyncTime", "2025-02-23T04:00:00Z",
                "syncStatus", "PARTIAL_FAILURE",
                "failedSources", List.of("inventory_db", "crm_api"),
                "successfulSources", List.of("hr_system", "finance_db"),
                "nextScheduledSync", "2025-02-23T08:00:00Z",
                "manualSyncAvailable", true
        ));
    }

    private ApiCallResult callB360DashboardApi(Map<String, String> params) {
        String dashboardId = params.getOrDefault("dashboardId", "DASH-001");
        return ApiCallResult.success("dashboard", Map.of(
                "dashboardId", dashboardId,
                "status", "SLOW_LOADING",
                "widgetCount", 25,
                "recommendedMax", 20,
                "slowestWidget", "sales-trend-chart",
                "avgLoadTime", "8.5s",
                "performanceScore", 45
        ));
    }

    private ApiCallResult callB360PermissionsApi(Map<String, String> params) {
        String userId = params.getOrDefault("userId", "USR-B360-001");
        return ApiCallResult.success("permissions", Map.of(
                "userId", userId,
                "currentRoles", List.of("VIEWER"),
                "requestedResource", "executive-dashboard",
                "requiredRole", "ANALYST",
                "accessDenied", true,
                "adminContact", "b360-admin@company.com"
        ));
    }

    private ApiCallResult callB360TicketCreateApi(Map<String, String> params) {
        String ticketId = "B360-TKT-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return ApiCallResult.success("ticketcreate", Map.of(
                "ticketId", ticketId,
                "title", params.getOrDefault("title", "B360 Support Request"),
                "priority", params.getOrDefault("priority", "MEDIUM"),
                "status", "OPEN",
                "queue", "b360-support-queue",
                "createdAt", Instant.now().toString()
        ));
    }

    // ─── VELOCITY APIs ─────────────────────────────────────────────────────────

    private ApiCallResult dispatchVelocity(String apiName, Map<String, String> params) {
        return switch (apiName.toLowerCase()) {
            case "build" -> callVelocityBuildApi(params);
            case "deployment" -> callVelocityDeploymentApi(params);
            case "pipeline" -> callVelocityPipelineApi(params);
            case "environment" -> callVelocityEnvironmentApi(params);
            case "agent" -> callVelocityAgentApi(params);
            case "ticketcreate" -> callVelocityTicketCreateApi(params);
            default -> ApiCallResult.failure(apiName, "Unknown Velocity API: '" + apiName + "'");
        };
    }

    private ApiCallResult callVelocityBuildApi(Map<String, String> params) {
        String buildId = params.getOrDefault("buildId", "BUILD-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        return ApiCallResult.success("build", Map.of(
                "buildId", buildId,
                "status", "FAILED",
                "failureReason", "Test suite 'integration-tests' failed: 3 tests failed",
                "stage", "test",
                "duration", "4m 32s",
                "artifacts", List.of(),
                "logs", "https://velocity.internal/builds/" + buildId + "/logs"
        ));
    }

    private ApiCallResult callVelocityDeploymentApi(Map<String, String> params) {
        String deploymentId = params.getOrDefault("deploymentId", "DEPLOY-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        return ApiCallResult.success("deployment", Map.of(
                "deploymentId", deploymentId,
                "status", "STUCK",
                "currentStage", "approval-gate",
                "waitingFor", "production-approvers",
                "waitingTime", "2h 15m",
                "targetEnvironment", "production",
                "canForce", false
        ));
    }

    private ApiCallResult callVelocityPipelineApi(Map<String, String> params) {
        String pipelineId = params.getOrDefault("pipelineId", "PIPE-001");
        return ApiCallResult.success("pipeline", Map.of(
                "pipelineId", pipelineId,
                "status", "ERROR",
                "errorType", "YAML_PARSE_ERROR",
                "errorMessage", "Line 45: Invalid stage reference 'deploy-prod' - stage not defined",
                "configFile", "velocity.yml",
                "lastSuccessfulRun", "2025-02-20T16:00:00Z"
        ));
    }

    private ApiCallResult callVelocityEnvironmentApi(Map<String, String> params) {
        String envName = params.getOrDefault("environment", "staging");
        return ApiCallResult.success("environment", Map.of(
                "environment", envName,
                "status", "MISCONFIGURED",
                "missingVariables", List.of("DATABASE_URL", "API_SECRET"),
                "expiredSecrets", List.of("AWS_ACCESS_KEY"),
                "lastUpdated", "2025-02-10T12:00:00Z",
                "updatedBy", "devops-bot"
        ));
    }

    private ApiCallResult callVelocityAgentApi(Map<String, String> params) {
        String agentId = params.getOrDefault("agentId", "AGENT-001");
        return ApiCallResult.success("agent", Map.of(
                "agentId", agentId,
                "status", "OFFLINE",
                "lastHeartbeat", "2025-02-23T06:45:00Z",
                "offlineDuration", "3h 15m",
                "agentType", "self-hosted",
                "os", "Ubuntu 22.04",
                "recommendedAction", "Regenerate agent token and restart agent service"
        ));
    }

    private ApiCallResult callVelocityTicketCreateApi(Map<String, String> params) {
        String ticketId = "VEL-TKT-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return ApiCallResult.success("ticketcreate", Map.of(
                "ticketId", ticketId,
                "title", params.getOrDefault("title", "Velocity Support Request"),
                "priority", params.getOrDefault("priority", "MEDIUM"),
                "status", "OPEN",
                "queue", "velocity-devops-queue",
                "createdAt", Instant.now().toString()
        ));
    }

    private void simulateLatency(long mockDelayMs) {
        if (mockDelayMs > 0) {
            try {
                Thread.sleep(mockDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Fallback method when circuit breaker is open or all retries failed.
     */
    @SuppressWarnings("unused")
    private List<ApiCallResult> callAllFallback(
            String productId, List<String> apiNames, Map<String, String> parameters, Throwable throwable) {
        log.error("         ProductAPI: CIRCUIT BREAKER OPEN | productId='{}' error='{}'",
                productId, throwable.getMessage());

        // Return failure results for all requested APIs
        List<ApiCallResult> results = new ArrayList<>();
        for (String apiName : apiNames) {
            results.add(ApiCallResult.failure(apiName,
                    "Service temporarily unavailable. Circuit breaker is open."));
        }
        return results;
    }
}

