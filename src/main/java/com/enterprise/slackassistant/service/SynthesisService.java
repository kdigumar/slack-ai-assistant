package com.enterprise.slackassistant.service;

import com.enterprise.slackassistant.dto.ApiCallResult;
import com.enterprise.slackassistant.dto.RagResult;
import com.fasterxml.jackson.core.JsonProcessingException;
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

import java.util.List;

/**
 * Synthesizes final responses using OpenAI LLM.
 * Protected by Resilience4j circuit breaker for fault tolerance.
 */
@Service
public class SynthesisService {

    private static final Logger log = LoggerFactory.getLogger(SynthesisService.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public SynthesisService(ChatClient chatClient, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Synthesizes a response from user query, API results, and RAG results.
     * Protected by circuit breaker and retry mechanisms.
     */
    @CircuitBreaker(name = "openai", fallbackMethod = "synthesizeFallback")
    @Retry(name = "openai")
    public String synthesize(String userQuery,
                             List<ApiCallResult> apiResults,
                             List<RagResult> ragResults) {

        String systemPromptText = buildSystemPrompt();
        String userPromptText = buildUserPrompt(userQuery, apiResults, ragResults);

        log.info("         Synthesis: sending to LLM for query='{}'", userQuery);
        log.info("         Synthesis: apiResults={} ragDocs={}", apiResults.size(), ragResults.size());
        log.info("         Synthesis: LLM prompt (user section):\n{}", userPromptText);

        Prompt prompt = new Prompt(List.of(
                new SystemMessage(systemPromptText),
                new UserMessage(userPromptText)
        ));

        ChatResponse response = chatClient.call(prompt);
        String content = response.getResult().getOutput().getContent();

        log.info("         Synthesis: LLM response ({} chars):\n{}", content.length(), content);
        return content.strip();
    }

    /**
     * Fallback method when circuit breaker is open or all retries failed.
     * Generates a reasonable response from API data without LLM.
     */
    @SuppressWarnings("unused")
    private String synthesizeFallback(String userQuery,
                                       List<ApiCallResult> apiResults,
                                       List<RagResult> ragResults,
                                       Throwable throwable) {
        log.error("         Synthesis: CIRCUIT BREAKER OPEN | error='{}'", throwable.getMessage());

        StringBuilder fallback = new StringBuilder();
        fallback.append(":warning: *AI synthesis is temporarily unavailable.*\n\n");
        fallback.append("Here's the raw data from our systems:\n\n");

        if (!apiResults.isEmpty()) {
            fallback.append("*API Results:*\n");
            for (ApiCallResult api : apiResults) {
                fallback.append("• `").append(api.getApiName()).append("`: ");
                if (api.isSuccess()) {
                    fallback.append("✓ Data retrieved\n");
                } else {
                    fallback.append("✗ ").append(api.getErrorMessage()).append("\n");
                }
            }
            fallback.append("\n");
        }

        if (!ragResults.isEmpty()) {
            fallback.append("*Relevant Documentation:*\n");
            for (RagResult rag : ragResults) {
                fallback.append("• _").append(rag.getTitle()).append("_\n");
            }
            fallback.append("\n");
        }

        fallback.append("Please try again in a few minutes, or contact support directly.");
        return fallback.toString();
    }

    private String buildSystemPrompt() {
        return """
                You are an expert enterprise support assistant.
                You receive: a user's support query, live API diagnostic data, and documentation.

                Guidelines:
                - Be concise but complete. Slack messages should be easy to read.
                - Use numbered steps for remediation actions.
                - Mention ticket IDs, user IDs, or status values from API data.
                - If the API shows a problem, explain what it means and how to fix it.
                - Do NOT mention "RAG", "LLM", or "API call" — speak naturally.
                - End with a friendly offer to help further.
                - Use plain Slack Markdown: *bold*, _italic_, numbered lists.
                - Maximum 400 words.
                """;
    }

    private String buildUserPrompt(String userQuery,
                                   List<ApiCallResult> apiResults,
                                   List<RagResult> ragResults) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== User Query ===\n").append(userQuery).append("\n\n");

        if (!apiResults.isEmpty()) {
            sb.append("=== Live API Data ===\n");
            for (ApiCallResult api : apiResults) {
                sb.append("API: ").append(api.getApiName())
                        .append(" | Success: ").append(api.isSuccess()).append("\n");
                if (api.isSuccess()) {
                    sb.append("Data: ").append(toJson(api.getData())).append("\n");
                } else {
                    sb.append("Error: ").append(api.getErrorMessage()).append("\n");
                }
                sb.append("\n");
            }
        }

        if (!ragResults.isEmpty()) {
            sb.append("=== Relevant Documentation ===\n");
            for (RagResult rag : ragResults) {
                sb.append("Title: ").append(rag.getTitle()).append("\n")
                        .append("Content: ").append(rag.getExcerpt()).append("\n\n");
            }
        }

        sb.append("=== Task ===\nWrite a helpful Slack response for the user.");
        return sb.toString();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return obj.toString();
        }
    }
}
