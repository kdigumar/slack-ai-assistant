package com.enterprise.slackassistant.config;

import com.enterprise.slackassistant.dto.ApiCallResult;
import com.enterprise.slackassistant.dto.IntentResult;
import com.enterprise.slackassistant.dto.RagResult;
import com.enterprise.slackassistant.exception.IntentNotFoundException;
import com.enterprise.slackassistant.model.IntentMapping;
import com.enterprise.slackassistant.service.*;
import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.context.builtin.EventContext;
import com.slack.api.methods.MethodsClient;
import com.slack.api.model.event.MessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Slack Bolt configuration with multi-product support.
 * Routes messages to the appropriate product handler based on channel name.
 * Uses distributed deduplication via Redis for Kubernetes scaling.
 */
@Configuration
public class SlackConfig {

    private static final Logger log = LoggerFactory.getLogger(SlackConfig.class);
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    @Bean
    public AppConfig appConfig(SlackProperties slackProperties) {
        return AppConfig.builder()
                .singleTeamBotToken(slackProperties.getBotToken())
                .signingSecret(slackProperties.getSigningSecret())
                .build();
    }

    @Bean
    public MethodsClient methodsClient(AppConfig appConfig) {
        return com.slack.api.Slack.getInstance().methods(appConfig.getSingleTeamBotToken());
    }

    @Bean
    public App slackApp(
            AppConfig appConfig,
            DeduplicationService deduplicationService,
            ProductRouterService productRouterService,
            CacheService cacheService,
            IntentDetectionService intentDetectionService,
            IntentMappingService intentMappingService,
            ProductApiService productApiService,
            RagService ragService,
            SynthesisService synthesisService,
            SlackService slackService) {

        App app = new App(appConfig);

        app.event(MessageEvent.class, (payload, ctx) -> {
            MessageEvent event = payload.getEvent();

            // ── Skip bot messages (prevent infinite loops) ──────────────
            if (event.getBotId() != null || event.getSubtype() != null) {
                return ctx.ack();
            }

            // ── Deduplicate using distributed Redis service ─────────────
            String eventId = event.getTs(); // message timestamp is unique per message
            if (!deduplicationService.tryMarkAsProcessed(eventId)) {
                log.info("⚡ DUPLICATE EVENT IGNORED | ts='{}' (Slack retry)", eventId);
                return ctx.ack();
            }

            // ── Ack IMMEDIATELY — no work before this point ─────────────
            String channelId = event.getChannel();
            String userId = event.getUser();
            String userMessage = event.getText();

            log.info("✉ Message received | userId='{}' channelId='{}' ts='{}': '{}'",
                    userId, channelId, eventId, userMessage);

            // All processing happens async AFTER the ack
            CompletableFuture.runAsync(
                    () -> processPipeline(ctx, channelId, userId, userMessage,
                            productRouterService, cacheService, intentDetectionService,
                            intentMappingService, productApiService, ragService,
                            synthesisService, slackService),
                    EXECUTOR);

            return ctx.ack();
        });

        return app;
    }

    private void processPipeline(
            EventContext ctx,
            String channelId, String userId, String userMessage,
            ProductRouterService productRouterService, CacheService cacheService,
            IntentDetectionService intentDetectionService, IntentMappingService intentMappingService,
            ProductApiService productApiService, RagService ragService,
            SynthesisService synthesisService, SlackService slackService) {

        long pipelineStart = System.currentTimeMillis();

        try {
            // ── Step 0: Resolve channel name (now inside async) ─────────
            String channelName = resolveChannelName(ctx, channelId);

            log.info("═══════════════════════════════════════════════════════════════");
            log.info("▶ PIPELINE START | channel='{}' userId='{}' message='{}'", channelName, userId, userMessage);
            log.info("═══════════════════════════════════════════════════════════════");

            // ── Step 1: Resolve ProductId via ProductRouterService ───────
            long t0 = System.currentTimeMillis();
            String productId;
            try {
                productId = productRouterService.resolveProductId(channelName);
            } catch (IllegalArgumentException e) {
                log.warn("  [Step 1/7] PRODUCT NOT FOUND | channel='{}' — sending fallback", channelName);
                slackService.postMessage(channelId,
                        ":warning: This channel is not configured for any product. " +
                        "Please contact your administrator to set up the channel-to-product mapping.");
                return;
            }
            log.info("  [Step 1/7] PRODUCT RESOLVED | channel='{}' → productId='{}' ({}ms)",
                    channelName, productId, System.currentTimeMillis() - t0);

            // ── Step 2: Detect intent via LLM ─────────────────────────────
            t0 = System.currentTimeMillis();
            IntentResult intentResult = intentDetectionService.detect(userMessage, productId);
            String intentName = intentResult.getIntentName();
            log.info("  [Step 2/7] INTENT DETECTED | intent='{}' params={} ({}ms)",
                    intentName, intentResult.getParameters(), System.currentTimeMillis() - t0);

            // ── Step 3: Cache check ───────────────────────────────────────
            String cacheKey = cacheService.buildKey(userId, productId + ":" + intentName);
            Optional<String> cached = cacheService.get(cacheKey);
            if (cached.isPresent()) {
                log.info("  [Step 3/7] CACHE HIT | key='{}' — skipping API+RAG+Synthesis", cacheKey);
                slackService.postMessage(channelId, cached.get());
                log.info("◀ PIPELINE END (cache hit) | total={}ms", System.currentTimeMillis() - pipelineStart);
                log.info("═══════════════════════════════════════════════════════════════\n");
                return;
            }
            log.info("  [Step 3/7] CACHE MISS | key='{}'", cacheKey);

            // ── Step 4: Intent → API mapping ──────────────────────────────
            IntentMapping mapping;
            try {
                mapping = intentMappingService.lookup(productId, intentName);
                log.info("  [Step 4/7] INTENT MAPPING | productId='{}' intent='{}' → apiNames={}",
                        productId, intentName, mapping.getApiNames());
            } catch (IntentNotFoundException e) {
                log.warn("  [Step 4/7] INTENT NOT FOUND | {}", e.getMessage());
                slackService.postMessage(channelId,
                        ":thinking_face: I couldn't match your request to a known action for " + productId + ". "
                        + "Could you provide more detail?");
                return;
            }

            // ── Step 5: Parallel API + RAG ────────────────────────────────
            log.info("  [Step 5/7] PARALLEL EXEC START | APIs={} + RAG query for product='{}'",
                    mapping.getApiNames(), productId);
            t0 = System.currentTimeMillis();

            final String finalProductId = productId;
            CompletableFuture<List<ApiCallResult>> apiFuture = CompletableFuture.supplyAsync(
                    () -> productApiService.callAll(finalProductId, mapping.getApiNames(), intentResult.getParameters()),
                    EXECUTOR);

            CompletableFuture<List<RagResult>> ragFuture = CompletableFuture.supplyAsync(
                    () -> ragService.retrieve(finalProductId, userMessage), EXECUTOR);

            CompletableFuture.allOf(apiFuture, ragFuture).join();

            List<ApiCallResult> apiResults = apiFuture.get();
            List<RagResult> ragResults = ragFuture.get();
            log.info("  [Step 5/7] PARALLEL EXEC DONE | apiResults={} ragDocs={} ({}ms)",
                    apiResults.size(), ragResults.size(), System.currentTimeMillis() - t0);

            // ── Step 6: Synthesis via LLM ─────────────────────────────────
            t0 = System.currentTimeMillis();
            String finalResponse = synthesisService.synthesize(userMessage, apiResults, ragResults);
            log.info("  [Step 6/7] SYNTHESIS DONE | responseLength={} chars ({}ms)",
                    finalResponse.length(), System.currentTimeMillis() - t0);

            // ── Step 7: Cache + post ──────────────────────────────────────
            cacheService.put(cacheKey, finalResponse);
            slackService.postMessage(channelId, finalResponse);
            log.info("  [Step 7/7] POSTED TO SLACK | channel='{}' cached as '{}'", channelId, cacheKey);

            log.info("◀ PIPELINE END (success) | total={}ms", System.currentTimeMillis() - pipelineStart);
            log.info("═══════════════════════════════════════════════════════════════\n");

        } catch (Exception e) {
            log.error("✖ PIPELINE ERROR | channel='{}' userId='{}' error='{}'",
                    channelId, userId, e.getMessage(), e);
            slackService.postErrorFallback(channelId);
        }
    }

    private String resolveChannelName(EventContext ctx, String channelId) {
        try {
            var info = ctx.client().conversationsInfo(r -> r.channel(channelId));
            if (info.isOk() && info.getChannel() != null) {
                return info.getChannel().getName();
            }
        } catch (Exception e) {
            log.warn("Could not resolve channel name for '{}': {}", channelId, e.getMessage());
        }
        return channelId;
    }
}
