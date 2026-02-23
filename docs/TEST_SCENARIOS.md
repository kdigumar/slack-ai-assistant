# Test Scenarios - Slack AI Support Assistant

## Overview

This document outlines test scenarios for the multi-product Slack AI Support Assistant covering Artemis, B360, and Velocity products.

---

## 1. Artemis Product Test Scenarios

### 1.1 User Account Issues

| Scenario | User Message | Expected Intent | Expected API | Expected Response |
|----------|--------------|-----------------|--------------|-------------------|
| Inactive account | "I am unable to access Artemis. Seeing No access page" | `User not active` | `user` | Account status info + reactivation steps |
| Account deactivated | "My Artemis account seems to be deactivated" | `User not active` | `user` | 90-day inactivity explanation + admin contact |
| Login issues | "Can't log into Artemis anymore" | `User not active` | `user` | Account verification + self-service options |

**Sample Test Message:**
```
User: "I am unable to access Artemis. Seeing No access page"

Expected Flow:
1. Intent Detection → "User not active"
2. API Call → user API returns {status: "INACTIVE", lastLogin: "2024-11-10"}
3. RAG → "Artemis Account Activation Guide" document
4. Synthesis → Friendly message with reactivation steps
```

### 1.2 Business Access Issues

| Scenario | User Message | Expected Intent | Expected API | Expected Response |
|----------|--------------|-----------------|--------------|-------------------|
| No business view | "I cannot see any business data in Artemis" | `Not able to view business` | `user`, `business` | Role assignment info + admin steps |
| Missing permissions | "Business reports are not showing up for me" | `Not able to view business` | `user`, `business` | BUSINESS_VIEWER role requirement |

**Sample Test Message:**
```
User: "I cannot see any business data in Artemis"

Expected Flow:
1. Intent Detection → "Not able to view business"
2. API Calls → user + business APIs (parallel)
3. RAG → "Business View Permissions" document
4. Synthesis → Role assignment instructions
```

### 1.3 Ticket Operations

| Scenario | User Message | Expected Intent | Expected API | Expected Response |
|----------|--------------|-----------------|--------------|-------------------|
| Create ticket | "I need to create a support ticket for a login issue" | `TicketCreation` | `ticketcreate` | Ticket ID + confirmation |
| View ticket | "What's the status of ticket TKT-12345?" | `ticketdetails` | `viewticket` | Ticket details + comments |

**Sample Test Message:**
```
User: "I need to create a support ticket"

Expected Flow:
1. Intent Detection → "TicketCreation" with params: {priority: "MEDIUM"}
2. API Call → ticketcreate returns {ticketId: "TKT-ABC123", status: "OPEN"}
3. Synthesis → "Your ticket TKT-ABC123 has been created..."
```

### 1.4 App Crashes

| Scenario | User Message | Expected Intent | Expected API | Expected Response |
|----------|--------------|-----------------|--------------|-------------------|
| Recent crashes | "Artemis keeps crashing when I try to generate reports" | `App Crash` | `appcrash` | Recent crash info + escalation path |
| Module errors | "Getting errors in the Auth module" | `App Crash` | `appcrash` | Crash diagnostics + #artemis-platform channel |

---

## 2. B360 Product Test Scenarios

### 2.1 Authentication Issues

| Scenario | User Message | Expected Intent | Expected API | Expected Response |
|----------|--------------|-----------------|--------------|-------------------|
| Login failed | "Can't log into B360, password not working" | `Login issue` | `auth` | Password expiry info + reset steps |
| Account locked | "My B360 account is locked" | `Login issue` | `auth` | Lockout status + unlock procedure |

**Sample Test Message:**
```
User: "Can't log into B360"

Expected Flow:
1. ProductRouter → channel "b360help" → productId "b360"
2. Intent Detection → "Login issue"
3. API Call → auth returns {authStatus: "FAILED", reason: "Password expired"}
4. RAG → "B360 Login Troubleshooting" document
5. Synthesis → Password reset instructions
```

### 2.2 Report Issues

| Scenario | User Message | Expected Intent | Expected API | Expected Response |
|----------|--------------|-----------------|--------------|-------------------|
| Report failed | "My report generation failed with an error" | `Report generation failed` | `report` | Error details + retry instructions |
| Slow reports | "Reports are taking too long to generate" | `Report generation failed` | `report` | Large report handling info |

### 2.3 Data Sync Issues

| Scenario | User Message | Expected Intent | Expected API | Expected Response |
|----------|--------------|-----------------|--------------|-------------------|
| Stale data | "B360 data seems outdated, not showing latest sales" | `Data sync problem` | `sync` | Last sync time + manual sync option |
| Sync failures | "Data from CRM is not syncing to B360" | `Data sync problem` | `sync` | Failed sources + resolution steps |

### 2.4 Dashboard Issues

| Scenario | User Message | Expected Intent | Expected API | Expected Response |
|----------|--------------|-----------------|--------------|-------------------|
| Slow dashboard | "My B360 dashboard is loading very slowly" | `Dashboard not loading` | `dashboard` | Widget count + optimization tips |
| Dashboard errors | "Dashboard widgets are not displaying" | `Dashboard not loading` | `dashboard` | Performance analyzer suggestion |

### 2.5 Permission Issues

| Scenario | User Message | Expected Intent | Expected API | Expected Response |
|----------|--------------|-----------------|--------------|-------------------|
| Access denied | "Getting permission denied when accessing executive reports" | `Permission denied` | `permissions` | Required role + admin contact |

---

## 3. Velocity Product Test Scenarios

### 3.1 Build Issues

| Scenario | User Message | Expected Intent | Expected API | Expected Response |
|----------|--------------|-----------------|--------------|-------------------|
| Build failed | "My build failed with test errors" | `Build failed` | `build` | Failure reason + logs link |
| Compilation error | "Getting dependency errors in my Velocity build" | `Build failed` | `build` | Clean build suggestion |

**Sample Test Message:**
```
User: "My build failed"

Expected Flow:
1. ProductRouter → channel "velocityhelp" → productId "velocity"
2. Intent Detection → "Build failed"
3. API Call → build returns {status: "FAILED", stage: "test", failureReason: "3 tests failed"}
4. RAG → "Velocity Build Troubleshooting" document
5. Synthesis → Test failure details + retry instructions
```

### 3.2 Deployment Issues

| Scenario | User Message | Expected Intent | Expected API | Expected Response |
|----------|--------------|-----------------|--------------|-------------------|
| Stuck deployment | "My deployment has been stuck for 2 hours" | `Deployment stuck` | `deployment` | Approval gate info + waiting time |
| Deployment failed | "Production deployment failed" | `Deployment stuck` | `deployment` | Rollback options + escalation |

### 3.3 Pipeline Issues

| Scenario | User Message | Expected Intent | Expected API | Expected Response |
|----------|--------------|-----------------|--------------|-------------------|
| Pipeline error | "Getting YAML parse error in my pipeline" | `Pipeline error` | `pipeline` | Error location + syntax fix |
| Pipeline config | "My pipeline stages are not running in order" | `Pipeline error` | `pipeline` | Stage reference validation |

### 3.4 Environment Issues

| Scenario | User Message | Expected Intent | Expected API | Expected Response |
|----------|--------------|-----------------|--------------|-------------------|
| Missing variables | "Environment variables are missing in staging" | `Environment configuration` | `environment` | Missing vars list + how to add |
| Expired secrets | "Getting secret expired errors" | `Environment configuration` | `environment` | Expired secrets + renewal steps |

### 3.5 Agent Issues

| Scenario | User Message | Expected Intent | Expected API | Expected Response |
|----------|--------------|-----------------|--------------|-------------------|
| Agent offline | "Build agent is showing offline" | `Agent offline` | `agent` | Last heartbeat + reconnection steps |
| Agent token | "Agent authentication failed" | `Agent offline` | `agent` | Token regeneration instructions |

---

## 4. Cross-Cutting Test Scenarios

### 4.1 Cache Behavior

| Scenario | Expected Behavior |
|----------|-------------------|
| First request | Cache MISS → Full pipeline → Store in cache |
| Repeat request (same user, same intent) | Cache HIT → Return cached response |
| Different user, same intent | Cache MISS (user-specific key) |
| After TTL expiry (10 min) | Cache MISS → Full pipeline |

### 4.2 Deduplication

| Scenario | Expected Behavior |
|----------|-------------------|
| Normal message | Process once |
| Slack retry (same ts) | Ignore duplicate |
| Similar message (different ts) | Process as new |

### 4.3 Circuit Breaker

| Scenario | Expected Behavior |
|----------|-------------------|
| OpenAI available | Normal processing |
| OpenAI timeout (>3 attempts) | Circuit OPEN → Fallback response |
| OpenAI recovers | Circuit HALF-OPEN → Test → CLOSED |

### 4.4 Unknown Channel

| Scenario | User Message | Expected Response |
|----------|--------------|-------------------|
| Unconfigured channel | Any message in `#random` | "This channel is not configured for any product" |

### 4.5 Unknown Intent

| Scenario | User Message | Expected Response |
|----------|--------------|-------------------|
| Unrecognized request | "What's the weather today?" | "I couldn't match your request to a known action" |

---

## 5. Performance Test Scenarios

| Scenario | Target | Measurement |
|----------|--------|-------------|
| End-to-end latency (cache miss) | < 5 seconds | Pipeline start → Slack response |
| End-to-end latency (cache hit) | < 500ms | Pipeline start → Slack response |
| Intent detection | < 2 seconds | OpenAI API call |
| Synthesis | < 3 seconds | OpenAI API call |
| API calls (mock) | < 100ms | ProductApiService |
| RAG retrieval | < 50ms | In-memory keyword search |

---

## 6. Error Handling Test Scenarios

| Scenario | Trigger | Expected Behavior |
|----------|---------|-------------------|
| OpenAI rate limit | 429 response | Retry with backoff → Fallback |
| OpenAI server error | 500 response | Retry → Circuit breaker → Fallback |
| Invalid JSON from LLM | Malformed response | Parse error → `unknown` intent |
| Redis unavailable | Connection refused | Fallback to in-memory cache |
| Kafka unavailable | Connection refused | Direct processing (no queue) |

---

## 7. Manual Testing Checklist

### Pre-requisites
- [ ] Application running on port 8080
- [ ] ngrok tunnel active
- [ ] Slack Event Subscriptions configured
- [ ] Bot invited to test channels

### Basic Flow Tests
- [ ] Send message in `#artemishelp` → Receive response
- [ ] Send message in `#b360help` → Receive response (different product)
- [ ] Send message in `#velocityhelp` → Receive response
- [ ] Send duplicate message → Only one response

### Cache Tests
- [ ] First message → Check logs for "CACHE MISS"
- [ ] Same message again → Check logs for "CACHE HIT"
- [ ] Wait 10+ minutes → Check logs for "CACHE MISS"

### Error Tests
- [ ] Stop OpenAI key → Circuit breaker fallback response
- [ ] Send message in unconfigured channel → Warning response

---

## 8. Sample Curl Commands for API Testing

```bash
# Health check
curl http://localhost:8080/actuator/health

# Simulate Slack event (for local testing without Slack)
curl -X POST http://localhost:8080/slack/events \
  -H "Content-Type: application/json" \
  -d '{
    "type": "event_callback",
    "event": {
      "type": "message",
      "text": "I cannot access Artemis",
      "user": "U12345",
      "channel": "C12345",
      "ts": "1234567890.123456"
    }
  }'
```

---

## 9. Log Verification Points

For each test, verify these log entries:

```
✉ Message received | userId='...' channelId='...'
═══════════════════════════════════════════════════════════════
▶ PIPELINE START | channel='artemishelp' userId='...'
═══════════════════════════════════════════════════════════════
  [Step 1/7] PRODUCT RESOLVED | channel='artemishelp' → productId='artemis'
  [Step 2/7] INTENT DETECTED | intent='User not active' params={}
  [Step 3/7] CACHE MISS | key='...'
  [Step 4/7] INTENT MAPPING | productId='artemis' intent='User not active' → apiNames=[user]
  [Step 5/7] PARALLEL EXEC START | APIs=[user] + RAG query
  [Step 5/7] PARALLEL EXEC DONE | apiResults=1 ragDocs=2
  [Step 6/7] SYNTHESIS DONE | responseLength=... chars
  [Step 7/7] POSTED TO SLACK | channel='...'
◀ PIPELINE END (success) | total=...ms
═══════════════════════════════════════════════════════════════
```

