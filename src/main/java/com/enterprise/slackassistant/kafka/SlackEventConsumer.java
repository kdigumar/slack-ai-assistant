package com.enterprise.slackassistant.kafka;

import com.enterprise.slackassistant.dto.ApiCallResult;
import com.enterprise.slackassistant.dto.IntentResult;
import com.enterprise.slackassistant.dto.RagResult;
import com.enterprise.slackassistant.exception.IntentNotFoundException;
import com.enterprise.slackassistant.model.IntentMapping;
import com.enterprise.slackassistant.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Consumes Slack events from Kafka and processes them through the pipeline.
 * This enables horizontal scaling and message durability.
 * Only active when kafka.enabled=true in configuration.
 */
@Service
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = false)
public class SlackEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(SlackEventConsumer.class);
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final ProductRouterService productRouterService;
    private final CacheService cacheService;
    private final IntentDetectionService intentDetectionService;
    private final IntentMappingService intentMappingService;
    private final ProductApiService productApiService;
    private final RagService ragService;
    private final SynthesisService synthesisService;
    private final SlackService slackService;

    public SlackEventConsumer(
            ProductRouterService productRouterService,
            CacheService cacheService,
            IntentDetectionService intentDetectionService,
            IntentMappingService intentMappingService,
            ProductApiService productApiService,
            RagService ragService,
            SynthesisService synthesisService,
            SlackService slackService) {
        this.productRouterService = productRouterService;
        this.cacheService = cacheService;
        this.intentDetectionService = intentDetectionService;
        this.intentMappingService = intentMappingService;
        this.productApiService = productApiService;
        this.ragService = ragService;
        this.synthesisService = synthesisService;
        this.slackService = slackService;

        log.info("SlackEventConsumer: initialized and listening for events");
    }

    @KafkaListener(
            topics = "${kafka.topic.slack-events:slack-events}",
            groupId = "${spring.kafka.consumer.group-id:slack-assistant-consumer}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(SlackEventMessage message) {
        log.info("Kafka: RECEIVED event | eventId='{}' channelId='{}' userId='{}'",
                message.getEventId(), message.getChannelId(), message.getUserId());

        processPipeline(message);
    }

    private void processPipeline(SlackEventMessage message) {
        long pipelineStart = System.currentTimeMillis();
        String channelId = message.getChannelId();
        String channelName = message.getChannelName();
        String userId = message.getUserId();
        String userMessage = message.getUserMessage();

        try {
            log.info("═══════════════════════════════════════════════════════════════");
            log.info("▶ KAFKA PIPELINE START | channel='{}' userId='{}' message='{}'",
                    channelName, userId, userMessage);
            log.info("═══════════════════════════════════════════════════════════════");

            // ── Step 1: Resolve ProductId ─────────────────────────────────
            long t0 = System.currentTimeMillis();
            String productId;
            try {
                productId = productRouterService.resolveProductId(channelName);
            } catch (IllegalArgumentException e) {
                log.warn("  [Step 1/7] PRODUCT NOT FOUND | channel='{}'", channelName);
                slackService.postMessage(channelId,
                        ":warning: This channel is not configured for any product.");
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
                log.info("  [Step 3/7] CACHE HIT | key='{}'", cacheKey);
                slackService.postMessage(channelId, cached.get());
                log.info("◀ KAFKA PIPELINE END (cache hit) | total={}ms",
                        System.currentTimeMillis() - pipelineStart);
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
                        ":thinking_face: I couldn't match your request to a known action.");
                return;
            }

            // ── Step 5: Parallel API + RAG ────────────────────────────────
            log.info("  [Step 5/7] PARALLEL EXEC START | APIs={} + RAG", mapping.getApiNames());
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
            log.info("  [Step 7/7] POSTED TO SLACK | channel='{}'", channelId);

            log.info("◀ KAFKA PIPELINE END (success) | total={}ms",
                    System.currentTimeMillis() - pipelineStart);
            log.info("═══════════════════════════════════════════════════════════════\n");

        } catch (Exception e) {
            log.error("✖ KAFKA PIPELINE ERROR | channel='{}' userId='{}' error='{}'",
                    channelId, userId, e.getMessage(), e);
            slackService.postErrorFallback(channelId);
        }
    }
}

