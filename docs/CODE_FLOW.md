# Code Flow Documentation - Slack AI Support Assistant

## Table of Contents
1. [High-Level Architecture](#1-high-level-architecture)
2. [Request Flow](#2-request-flow)
3. [Component Details](#3-component-details)
4. [Data Flow](#4-data-flow)
5. [Sequence Diagrams](#5-sequence-diagrams)
6. [Class Relationships](#6-class-relationships)

---

## 1. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              SLACK WORKSPACE                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                       │
│  │ #artemishelp │  │  #b360help   │  │#velocityhelp │                       │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘                       │
└─────────┼─────────────────┼─────────────────┼───────────────────────────────┘
          │                 │                 │
          └────────────────┬┴─────────────────┘
                           │ Slack Events API (HTTP POST)
                           ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         SPRING BOOT APPLICATION                              │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                    ENTRY LAYER                                       │    │
│  │  ┌────────────────────┐    ┌────────────────────┐                   │    │
│  │  │ SlackAppController │───▶│    SlackConfig     │                   │    │
│  │  │  (Servlet @        │    │  (Bolt App Bean    │                   │    │
│  │  │   /slack/events)   │    │   + Event Handler) │                   │    │
│  │  └────────────────────┘    └─────────┬──────────┘                   │    │
│  └──────────────────────────────────────┼──────────────────────────────┘    │
│                                         │                                    │
│                                         ▼                                    │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                    SERVICE LAYER                                     │    │
│  │                                                                      │    │
│  │  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐  │    │
│  │  │ ProductRouter   │───▶│ IntentDetection │───▶│ IntentMapping   │  │    │
│  │  │ Service         │    │ Service         │    │ Service         │  │    │
│  │  └─────────────────┘    └─────────────────┘    └─────────────────┘  │    │
│  │          │                      │                      │            │    │
│  │          ▼                      ▼                      ▼            │    │
│  │  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐  │    │
│  │  │ ProductConfig   │    │   OpenAI API    │    │ intent-mappings │  │    │
│  │  │ (application.yml)│    │   (GPT-4o)     │    │ /*.json         │  │    │
│  │  └─────────────────┘    └─────────────────┘    └─────────────────┘  │    │
│  │                                                                      │    │
│  │  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐  │    │
│  │  │ ProductApi      │───▶│   RagService    │───▶│ SynthesisService│  │    │
│  │  │ Service         │    │                 │    │                 │  │    │
│  │  └─────────────────┘    └─────────────────┘    └─────────────────┘  │    │
│  │          │                      │                      │            │    │
│  │          ▼                      ▼                      ▼            │    │
│  │  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐  │    │
│  │  │ Mock APIs       │    │ rag-docs/*.json │    │   OpenAI API    │  │    │
│  │  │ (per product)   │    │ (per product)   │    │   (GPT-4o)      │  │    │
│  │  └─────────────────┘    └─────────────────┘    └─────────────────┘  │    │
│  │                                                                      │    │
│  └──────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                    INFRASTRUCTURE LAYER                              │    │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐               │    │
│  │  │ CacheService │  │ Deduplication│  │ SlackService │               │    │
│  │  │   (Redis)    │  │   (Redis)    │  │ (Post Msg)   │               │    │
│  │  └──────────────┘  └──────────────┘  └──────────────┘               │    │
│  └──────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Request Flow

### 2.1 Complete Request Lifecycle

```
User types in Slack
        │
        ▼
┌───────────────────┐
│  Slack Platform   │
│  (Events API)     │
└────────┬──────────┘
         │ HTTP POST to /slack/events
         ▼
┌───────────────────┐
│  ngrok / Ingress  │
│  (HTTPS tunnel)   │
└────────┬──────────┘
         │
         ▼
┌───────────────────┐
│ SlackAppController│  ← Servlet registration
│ (Spring Bean)     │
└────────┬──────────┘
         │
         ▼
┌───────────────────┐
│  Bolt SDK App     │  ← Signature verification
│  (SlackConfig)    │     JSON parsing
└────────┬──────────┘
         │
         ▼
┌───────────────────┐
│ Deduplication     │  ← Check if event already processed
│ Service           │
└────────┬──────────┘
         │
         ├──────────────────────────────────┐
         │                                  │
    [New Event]                      [Duplicate]
         │                                  │
         ▼                                  ▼
┌───────────────────┐              ┌───────────────────┐
│    ctx.ack()      │              │  Ignore + ack()   │
│ (Acknowledge)     │              │                   │
└────────┬──────────┘              └───────────────────┘
         │
         ▼
┌───────────────────┐
│ CompletableFuture │  ← Async processing
│ .runAsync()       │
└────────┬──────────┘
         │
         ▼
┌───────────────────┐
│  processPipeline  │  ← 7-step pipeline
│  (SlackConfig)    │
└────────┬──────────┘
         │
         ▼
    [7-Step Pipeline]
         │
         ▼
┌───────────────────┐
│  SlackService     │  ← Post response
│  .postMessage()   │
└────────┬──────────┘
         │
         ▼
┌───────────────────┐
│  Slack Platform   │
│  (Web API)        │
└────────┬──────────┘
         │
         ▼
   Response appears
   in Slack channel
```

### 2.2 The 7-Step Pipeline

```
processPipeline()
        │
        ▼
┌─────────────────────────────────────────────────────────────┐
│ STEP 1: Resolve Product                                      │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ ProductRouterService.resolveProductId(channelName)      │ │
│ │                                                          │ │
│ │ "artemishelp" ──────────────────────▶ "artemis"         │ │
│ │ "b360help" ─────────────────────────▶ "b360"            │ │
│ │ "velocityhelp" ─────────────────────▶ "velocity"        │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────┐
│ STEP 2: Detect Intent (OpenAI GPT-4o)                        │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ IntentDetectionService.detect(userMessage, productId)   │ │
│ │                                                          │ │
│ │ User: "I can't access Artemis"                          │ │
│ │           │                                              │ │
│ │           ▼                                              │ │
│ │ ┌─────────────────────────────────────────────────────┐ │ │
│ │ │ System Prompt:                                       │ │ │
│ │ │ "Classify into one of these intents:                │ │ │
│ │ │  - User not active                                  │ │ │
│ │ │  - Not able to view business                        │ │ │
│ │ │  - TicketCreation ..."                              │ │ │
│ │ └─────────────────────────────────────────────────────┘ │ │
│ │           │                                              │ │
│ │           ▼                                              │ │
│ │ IntentResult: {intentName: "User not active", params: {}}│ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────┐
│ STEP 3: Check Cache                                          │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ CacheService.get(userId + ":" + productId + ":" + intent)│ │
│ │                                                          │ │
│ │ Key: "U12345:artemis:User not active"                   │ │
│ │                                                          │ │
│ │ ┌─────────────┐              ┌─────────────┐            │ │
│ │ │  CACHE HIT  │──▶ Return    │ CACHE MISS  │──▶ Continue│ │
│ │ │             │   cached     │             │   pipeline │ │
│ │ └─────────────┘   response   └─────────────┘            │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────┐
│ STEP 4: Lookup Intent Mapping                                │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ IntentMappingService.lookup(productId, intentName)      │ │
│ │                                                          │ │
│ │ File: intent-mappings/artemis.json                      │ │
│ │ ┌─────────────────────────────────────────────────────┐ │ │
│ │ │ {                                                    │ │ │
│ │ │   "appId": "artemis",                               │ │ │
│ │ │   "intentName": "User not active",                  │ │ │
│ │ │   "apiNames": ["user"]  ◀─── APIs to call           │ │ │
│ │ │ }                                                    │ │ │
│ │ └─────────────────────────────────────────────────────┘ │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────┐
│ STEP 5: Parallel API + RAG Execution                         │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │              CompletableFuture.allOf()                   │ │
│ │                        │                                 │ │
│ │         ┌──────────────┴──────────────┐                 │ │
│ │         ▼                             ▼                 │ │
│ │ ┌───────────────────┐      ┌───────────────────┐       │ │
│ │ │ ProductApiService │      │    RagService     │       │ │
│ │ │ .callAll(         │      │ .retrieve(        │       │ │
│ │ │   "artemis",      │      │   "artemis",      │       │ │
│ │ │   ["user"],       │      │   userMessage)    │       │ │
│ │ │   params)         │      │                   │       │ │
│ │ └─────────┬─────────┘      └─────────┬─────────┘       │ │
│ │           │                          │                  │ │
│ │           ▼                          ▼                  │ │
│ │ ┌───────────────────┐      ┌───────────────────┐       │ │
│ │ │ ApiCallResult:    │      │ RagResult[]:      │       │ │
│ │ │ {                 │      │ - "Account        │       │ │
│ │ │   userId: "123",  │      │    Activation     │       │ │
│ │ │   status:INACTIVE,│      │    Guide"         │       │ │
│ │ │   lastLogin:...   │      │ - "Business View  │       │ │
│ │ │ }                 │      │    Permissions"   │       │ │
│ │ └───────────────────┘      └───────────────────┘       │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────┐
│ STEP 6: Synthesize Response (OpenAI GPT-4o)                  │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ SynthesisService.synthesize(query, apiResults, ragDocs) │ │
│ │                                                          │ │
│ │ ┌───────────────────────────────────────────────────────┐│ │
│ │ │ Prompt to GPT-4o:                                      ││ │
│ │ │ === User Query ===                                     ││ │
│ │ │ "I can't access Artemis"                              ││ │
│ │ │                                                        ││ │
│ │ │ === Live API Data ===                                  ││ │
│ │ │ API: user | Success: true                              ││ │
│ │ │ Data: {status: "INACTIVE", lastLogin: "2024-11-10"}   ││ │
│ │ │                                                        ││ │
│ │ │ === Relevant Documentation ===                         ││ │
│ │ │ Title: Artemis Account Activation Guide                ││ │
│ │ │ Content: "Accounts are auto-deactivated after 90..."  ││ │
│ │ │                                                        ││ │
│ │ │ === Task ===                                           ││ │
│ │ │ Write a helpful Slack response for the user.          ││ │
│ │ └───────────────────────────────────────────────────────┘│ │
│ │                         │                                 │ │
│ │                         ▼                                 │ │
│ │ "Hi! It looks like your account is inactive due to..."   │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────┐
│ STEP 7: Cache + Post to Slack                                │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ 1. CacheService.put(cacheKey, response)                 │ │
│ │    └─▶ Store in Redis (TTL: 10 min)                     │ │
│ │                                                          │ │
│ │ 2. SlackService.postMessage(channelId, response)        │ │
│ │    └─▶ Slack Web API: chat.postMessage                  │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. Component Details

### 3.1 Entry Point Components

```
┌────────────────────────────────────────────────────────────────┐
│                    SlackAppController.java                      │
├────────────────────────────────────────────────────────────────┤
│ @Configuration                                                  │
│ @Bean ServletRegistrationBean<SlackAppServlet>                 │
│                                                                 │
│ Purpose:                                                        │
│ - Registers Bolt servlet at /slack/events                      │
│ - Receives App bean (from SlackConfig) for event handling      │
│                                                                 │
│ Request Path:                                                   │
│ HTTP POST /slack/events ──▶ SlackAppServlet ──▶ App.event()    │
└────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────────┐
│                      SlackConfig.java                           │
├────────────────────────────────────────────────────────────────┤
│ @Configuration                                                  │
│ @Bean App slackApp(...)                                        │
│ @Bean AppConfig appConfig(SlackProperties)                     │
│ @Bean MethodsClient methodsClient(AppConfig)                   │
│                                                                 │
│ Purpose:                                                        │
│ - Creates Bolt App with MessageEvent handler                   │
│ - Orchestrates the 7-step pipeline                             │
│ - Handles deduplication and async processing                   │
│                                                                 │
│ Key Logic:                                                      │
│ app.event(MessageEvent.class, (payload, ctx) -> {              │
│     // Skip bots, deduplicate, ack, process async              │
│ })                                                              │
└────────────────────────────────────────────────────────────────┘
```

### 3.2 Service Layer Components

```
┌────────────────────────────────────────────────────────────────┐
│               ProductRouterService.java                         │
├────────────────────────────────────────────────────────────────┤
│ @Service                                                        │
│                                                                 │
│ Dependencies:                                                   │
│ - ProductConfig (injected)                                     │
│                                                                 │
│ Key Methods:                                                    │
│ - resolveProductId(channelName) → productId                    │
│ - getProductDefinition(productId) → ProductDefinition          │
│                                                                 │
│ Data Structure:                                                 │
│ channelToProductId: Map<String, String>                        │
│   "artemishelp" → "artemis"                                    │
│   "b360help" → "b360"                                          │
│   "velocityhelp" → "velocity"                                  │
└────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│              IntentDetectionService.java                        │
├────────────────────────────────────────────────────────────────┤
│ @Service                                                        │
│ @CircuitBreaker(name = "openai")                               │
│ @Retry(name = "openai")                                        │
│                                                                 │
│ Dependencies:                                                   │
│ - ChatClient (Spring AI)                                       │
│ - IntentMappingService                                         │
│ - ObjectMapper                                                  │
│                                                                 │
│ Key Methods:                                                    │
│ - detect(userMessage, productId) → IntentResult                │
│ - detectFallback(...) → "service_unavailable" intent           │
│                                                                 │
│ LLM Interaction:                                                │
│ 1. Build system prompt with known intents                      │
│ 2. Send to GPT-4o                                              │
│ 3. Parse JSON response                                         │
│ 4. Return IntentResult{intentName, parameters}                 │
└────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│              IntentMappingService.java                          │
├────────────────────────────────────────────────────────────────┤
│ @Service                                                        │
│                                                                 │
│ Dependencies:                                                   │
│ - ObjectMapper                                                  │
│ - ProductConfig                                                 │
│                                                                 │
│ Key Methods:                                                    │
│ - lookup(productId, intentName) → IntentMapping                │
│ - getKnownIntents(productId) → List<String>                    │
│                                                                 │
│ Data Structure:                                                 │
│ mappingsByProduct: Map<String, Map<String, IntentMapping>>     │
│   "artemis" → {                                                │
│     "User not active" → {apiNames: ["user"]}                   │
│     "Not able to view business" → {apiNames: ["user","business"]}│
│   }                                                             │
└────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│                ProductApiService.java                           │
├────────────────────────────────────────────────────────────────┤
│ @Service                                                        │
│ @CircuitBreaker(name = "external-api")                         │
│ @Retry(name = "external-api")                                  │
│                                                                 │
│ Key Methods:                                                    │
│ - callAll(productId, apiNames, params) → List<ApiCallResult>   │
│ - dispatch(productId, apiName, params) → ApiCallResult         │
│                                                                 │
│ Product Routing:                                                │
│ switch(productId):                                              │
│   "artemis" → dispatchArtemis(apiName)                         │
│   "b360" → dispatchB360(apiName)                               │
│   "velocity" → dispatchVelocity(apiName)                       │
│                                                                 │
│ Mock APIs per Product:                                          │
│ Artemis: user, business, ticketcreate, viewticket, updateuser  │
│ B360: auth, report, sync, dashboard, permissions               │
│ Velocity: build, deployment, pipeline, environment, agent      │
└────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│                   RagService.java                               │
├────────────────────────────────────────────────────────────────┤
│ @Service                                                        │
│                                                                 │
│ Key Methods:                                                    │
│ - retrieve(productId, userQuery) → List<RagResult>             │
│ - score(doc, queryLower, queryTokens) → RagResult              │
│                                                                 │
│ Retrieval Algorithm:                                            │
│ 1. Load product-specific docs (rag-docs/{product}.json)        │
│ 2. Tokenize user query                                         │
│ 3. Score each doc by keyword overlap                           │
│ 4. Return top 3 matches                                        │
│                                                                 │
│ Data Structure:                                                 │
│ documentsByProduct: Map<String, List<RagDocument>>             │
└────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│                SynthesisService.java                            │
├────────────────────────────────────────────────────────────────┤
│ @Service                                                        │
│ @CircuitBreaker(name = "openai")                               │
│ @Retry(name = "openai")                                        │
│                                                                 │
│ Key Methods:                                                    │
│ - synthesize(query, apiResults, ragResults) → String           │
│ - synthesizeFallback(...) → Raw data fallback message          │
│                                                                 │
│ LLM Interaction:                                                │
│ 1. Build prompt with user query + API data + RAG docs          │
│ 2. Send to GPT-4o                                              │
│ 3. Return natural language response for Slack                  │
└────────────────────────────────────────────────────────────────┘
```

### 3.3 Infrastructure Components

```
┌────────────────────────────────────────────────────────────────┐
│                   CacheService.java                             │
├────────────────────────────────────────────────────────────────┤
│ @Service                                                        │
│                                                                 │
│ Storage:                                                        │
│ - Primary: Redis (StringRedisTemplate)                         │
│ - Fallback: In-memory ConcurrentHashMap                        │
│                                                                 │
│ Key Pattern:                                                    │
│ "slack:cache:{userId}:{productId}:{intentName}"                │
│                                                                 │
│ Key Methods:                                                    │
│ - get(key) → Optional<String>                                  │
│ - put(key, response)                                           │
│ - buildKey(userId, intentKey) → String                         │
│                                                                 │
│ TTL: 10 minutes (configurable)                                  │
└────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│               DeduplicationService.java                         │
├────────────────────────────────────────────────────────────────┤
│ @Service                                                        │
│                                                                 │
│ Storage:                                                        │
│ - Primary: Redis SET with TTL                                  │
│ - Fallback: In-memory LinkedHashMap (LRU, max 500)             │
│                                                                 │
│ Key Pattern:                                                    │
│ "slack:dedup:{eventTs}"                                        │
│                                                                 │
│ Key Methods:                                                    │
│ - tryMarkAsProcessed(eventId) → boolean                        │
│   true = new event, false = duplicate                          │
│                                                                 │
│ TTL: 5 minutes                                                  │
└────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│                   SlackService.java                             │
├────────────────────────────────────────────────────────────────┤
│ @Service                                                        │
│                                                                 │
│ Dependencies:                                                   │
│ - MethodsClient (Slack SDK)                                    │
│                                                                 │
│ Key Methods:                                                    │
│ - postMessage(channelId, text)                                 │
│ - postErrorFallback(channelId)                                 │
│                                                                 │
│ API Used:                                                       │
│ - chat.postMessage (Slack Web API)                             │
└────────────────────────────────────────────────────────────────┘
```

---

## 4. Data Flow

### 4.1 Configuration Data Flow

```
application.yml
      │
      ▼
┌─────────────────┐
│ ProductConfig   │ ◀── @ConfigurationProperties
│ (Spring Bean)   │
└────────┬────────┘
         │
         ├────────────────────────────────────┐
         ▼                                    ▼
┌─────────────────┐               ┌─────────────────┐
│ ProductRouter   │               │ IntentMapping   │
│ Service         │               │ Service         │
│                 │               │                 │
│ (channel→product│               │ (loads per-     │
│  mapping)       │               │  product JSON)  │
└─────────────────┘               └─────────────────┘
```

### 4.2 Message Data Flow

```
Slack Message
      │
      │ {type: "message", text: "...", user: "U123", channel: "C456", ts: "123.456"}
      ▼
┌─────────────────┐
│ MessageEvent    │
│ (Bolt SDK)      │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ IntentResult    │  {intentName: "User not active", parameters: {}}
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ IntentMapping   │  {appId: "artemis", intentName: "...", apiNames: ["user"]}
└────────┬────────┘
         │
         ├──────────────────────────┐
         ▼                          ▼
┌─────────────────┐      ┌─────────────────┐
│ ApiCallResult[] │      │ RagResult[]     │
│ {apiName: "user"│      │ {title: "...",  │
│  data: {...}}   │      │  content: "..."}│
└────────┬────────┘      └────────┬────────┘
         │                        │
         └───────────┬────────────┘
                     ▼
         ┌─────────────────┐
         │ Synthesized     │
         │ Response        │  "Hi! Your account is inactive..."
         │ (String)        │
         └────────┬────────┘
                  │
                  ▼
         ┌─────────────────┐
         │ Slack Message   │  Posted to channel
         └─────────────────┘
```

---

## 5. Sequence Diagrams

### 5.1 Happy Path Sequence

```
User          Slack         App           Router        Intent       Mapping      API          RAG          Synth        Cache        Slack
 │              │             │             │             │             │           │            │             │            │            │
 │──message────▶│             │             │             │             │           │            │             │            │            │
 │              │──POST──────▶│             │             │             │           │            │             │            │            │
 │              │             │──resolve───▶│             │             │           │            │             │            │            │
 │              │             │◀──artemis───│             │             │           │            │             │            │            │
 │              │             │──detect────────────────▶│             │           │            │             │            │            │
 │              │             │             │             │──GPT-4o───▶│           │            │             │            │            │
 │              │             │             │             │◀──intent───│           │            │             │            │            │
 │              │             │◀──intent result──────────│             │           │            │             │            │            │
 │              │             │──cache.get───────────────────────────────────────────────────────────────────▶│            │
 │              │             │◀──MISS───────────────────────────────────────────────────────────────────────│            │
 │              │             │──lookup────────────────────────────────▶│           │            │             │            │            │
 │              │             │◀──apiNames=["user"]─────────────────────│           │            │             │            │            │
 │              │             │                                         │           │            │             │            │            │
 │              │             │──────────────────parallel───────────────────────────▶│           │             │            │            │
 │              │             │                                         │           │◀──call────│             │            │            │
 │              │             │                                         │           │──result──▶│             │            │            │
 │              │             │──────────────────parallel────────────────────────────────────────▶│            │            │
 │              │             │                                         │           │            │◀──retrieve─│            │            │
 │              │             │                                         │           │            │──docs─────▶│            │            │
 │              │             │◀───────────join parallel────────────────────────────────────────────────────│            │            │
 │              │             │──synthesize──────────────────────────────────────────────────────────────────▶│            │            │
 │              │             │             │             │             │           │            │◀──GPT-4o──│            │            │
 │              │             │             │             │             │           │            │──response─▶│            │            │
 │              │             │◀──response─────────────────────────────────────────────────────────────────────│            │            │
 │              │             │──cache.put───────────────────────────────────────────────────────────────────▶│            │
 │              │             │──post────────────────────────────────────────────────────────────────────────────────────────▶│
 │              │◀────────────────────────────────────────────────────────────────────────────────────────────────────────────│
 │◀──response───│             │             │             │             │           │            │             │            │            │
```

### 5.2 Cache Hit Sequence

```
User          Slack         App           Cache
 │              │             │             │
 │──message────▶│             │             │
 │              │──POST──────▶│             │
 │              │             │──get───────▶│
 │              │             │◀──HIT───────│  (cached response)
 │              │             │──post──────▶│
 │◀──response───│◀────────────│             │
                              │             │
                    (skipped: intent, API, RAG, synthesis)
```

### 5.3 Circuit Breaker Sequence

```
User          App           OpenAI        CircuitBreaker
 │             │              │               │
 │──message──▶│              │               │
 │             │──call──────▶│               │
 │             │◀──timeout───│               │
 │             │──retry─────▶│               │
 │             │◀──timeout───│               │
 │             │──retry─────▶│               │
 │             │◀──timeout───│               │
 │             │             │──open────────▶│
 │             │◀──fallback──│               │
 │◀──fallback──│             │               │
               │             │               │
      "AI synthesis temporarily unavailable..."
```

---

## 6. Class Relationships

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           CONFIGURATION                                      │
│                                                                              │
│  ┌────────────────┐       ┌────────────────┐       ┌────────────────┐       │
│  │ ProductConfig  │◀──────│ SlackProperties│       │   AppConfig    │       │
│  │                │       │                │       │   (Bolt SDK)   │       │
│  │ +definitions   │       │ +botToken      │       │                │       │
│  │  Map<String,   │       │ +signingSecret │       │                │       │
│  │  ProductDef>   │       │                │       │                │       │
│  └───────┬────────┘       └────────────────┘       └────────────────┘       │
│          │                                                                   │
└──────────┼───────────────────────────────────────────────────────────────────┘
           │
           │ injects
           ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                             SERVICES                                         │
│                                                                              │
│  ┌──────────────────┐                                                        │
│  │ ProductRouter    │                                                        │
│  │ Service          │                                                        │
│  │                  │                                                        │
│  │ +resolveProductId│                                                        │
│  └────────┬─────────┘                                                        │
│           │                                                                  │
│           │ uses                                                             │
│           ▼                                                                  │
│  ┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐     │
│  │ IntentDetection  │────▶│ IntentMapping    │────▶│ ProductApi       │     │
│  │ Service          │     │ Service          │     │ Service          │     │
│  │                  │     │                  │     │                  │     │
│  │ @CircuitBreaker  │     │ +lookup()        │     │ @CircuitBreaker  │     │
│  │ +detect()        │     │ +getKnownIntents │     │ +callAll()       │     │
│  └────────┬─────────┘     └──────────────────┘     └──────────────────┘     │
│           │                                                                  │
│           │ calls                                                            │
│           ▼                                                                  │
│  ┌──────────────────┐     ┌──────────────────┐                              │
│  │ ChatClient       │     │ RagService       │                              │
│  │ (Spring AI)      │     │                  │                              │
│  │                  │     │ +retrieve()      │                              │
│  └──────────────────┘     └──────────────────┘                              │
│                                                                              │
│  ┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐     │
│  │ Synthesis        │────▶│ CacheService     │────▶│ SlackService     │     │
│  │ Service          │     │                  │     │                  │     │
│  │                  │     │ +get() / +put()  │     │ +postMessage()   │     │
│  │ @CircuitBreaker  │     │ (Redis/Memory)   │     │                  │     │
│  │ +synthesize()    │     └──────────────────┘     └──────────────────┘     │
│  └──────────────────┘                                                        │
│                                                                              │
│  ┌──────────────────┐                                                        │
│  │ Deduplication    │                                                        │
│  │ Service          │                                                        │
│  │                  │                                                        │
│  │ +tryMarkAsProc() │                                                        │
│  │ (Redis/Memory)   │                                                        │
│  └──────────────────┘                                                        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Summary

The code follows a clean, layered architecture:

1. **Entry**: `SlackAppController` → `SlackConfig` (Bolt App)
2. **Routing**: `ProductRouterService` (channel → product)
3. **Intelligence**: `IntentDetectionService` + `RagService` (OpenAI + docs)
4. **Execution**: `ProductApiService` (mock APIs per product)
5. **Output**: `SynthesisService` → `SlackService`
6. **Infrastructure**: `CacheService` + `DeduplicationService` (Redis)
7. **Resilience**: `@CircuitBreaker` + `@Retry` (Resilience4j)

