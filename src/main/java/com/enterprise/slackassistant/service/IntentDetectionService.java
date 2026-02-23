package com.enterprise.slackassistant.service;

import com.enterprise.slackassistant.dto.IntentResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Detects user intent using OpenAI LLM based on the product's known intents.
 * Protected by Resilience4j circuit breaker for fault tolerance.
 */
@Service
public class IntentDetectionService {

    private static final Logger log = LoggerFactory.getLogger(IntentDetectionService.class);

    private final ChatClient chatClient;
    private final IntentMappingService intentMappingService;
    private final ObjectMapper objectMapper;

    public IntentDetectionService(ChatClient chatClient,
                                  IntentMappingService intentMappingService,
                                  ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.intentMappingService = intentMappingService;
        this.objectMapper = objectMapper;
    }

    /**
     * Detects the intent from a user message for a specific product.
     * Protected by circuit breaker and retry mechanisms.
     *
     * @param userMessage the user's message text
     * @param productId   the product ID (e.g., "artemis", "b360", "velocity")
     * @return IntentResult with the detected intent and extracted parameters
     */
    @CircuitBreaker(name = "openai", fallbackMethod = "detectFallback")
    @Retry(name = "openai")
    public IntentResult detect(String userMessage, String productId) {
        List<String> knownIntents = intentMappingService.getKnownIntents(productId);
        String systemPromptText = buildSystemPrompt(productId, knownIntents);

        log.info("         IntentDetection: sending to LLM | productId='{}' knownIntents={}", productId, knownIntents);
        log.info("         IntentDetection: user message → '{}'", userMessage);

        Prompt prompt = new Prompt(List.of(
                new SystemMessage(systemPromptText),
                new UserMessage("User message: " + userMessage)
        ));

        ChatResponse response = chatClient.call(prompt);
        String rawResponse = response.getResult().getOutput().getContent();

        log.info("         IntentDetection: LLM raw response → {}", rawResponse);
        return parseResponse(rawResponse);
    }

    /**
     * Fallback method when circuit breaker is open or all retries failed.
     */
    @SuppressWarnings("unused")
    private IntentResult detectFallback(String userMessage, String productId, Throwable throwable) {
        log.error("         IntentDetection: CIRCUIT BREAKER OPEN | productId='{}' error='{}'",
                productId, throwable.getMessage());

        // Return a generic intent that can be handled gracefully
        return IntentResult.of("service_unavailable", Map.of(
                "reason", "OpenAI service temporarily unavailable",
                "fallback", "true"
        ));
    }

    private String buildSystemPrompt(String productId, List<String> knownIntents) {
        String intentList = String.join("\n", knownIntents.stream()
                .map(i -> "  - " + i).toList());

        String productDescription = getProductDescription(productId);

        return """
                You are an enterprise support assistant for %s.
                %s
                
                Classify the user's message into exactly one intent from:

                %s

                Rules:
                1. Choose the single best-matching intent.
                2. Extract parameters (ticketId, userId, priority, etc.) if mentioned.
                3. Respond ONLY with valid JSON — no markdown, no explanation:
                   {"intentName": "<intent>", "parameters": {"<key>": "<value>"}}
                """.formatted(productId.toUpperCase(), productDescription, intentList);
    }

    private String getProductDescription(String productId) {
        return switch (productId.toLowerCase()) {
            case "artemis" -> "Artemis is a user management and access control platform.";
            case "b360" -> "B360 is a business intelligence and reporting platform.";
            case "velocity" -> "Velocity is a CI/CD and deployment automation platform.";
            default -> "This is an enterprise application.";
        };
    }

    private IntentResult parseResponse(String rawResponse) {
        try {
            String cleaned = rawResponse.strip();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceFirst("```(?:json)?\\s*", "")
                        .replaceAll("```\\s*$", "").strip();
            }

            JsonNode root = objectMapper.readTree(cleaned);
            String intentName = root.path("intentName").asText();

            Map<String, String> parameters = new HashMap<>();
            JsonNode params = root.path("parameters");
            if (params.isObject()) {
                params.fields().forEachRemaining(e -> parameters.put(e.getKey(), e.getValue().asText()));
            }

            log.info("         IntentDetection: parsed | intent='{}' params={}", intentName, parameters);
            return IntentResult.of(intentName, parameters);

        } catch (Exception e) {
            log.error("         IntentDetection: FAILED to parse response: '{}'", rawResponse, e);
            return IntentResult.of("unknown", Map.of());
        }
    }
}
