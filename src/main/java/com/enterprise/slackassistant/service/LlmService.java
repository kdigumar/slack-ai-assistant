package com.enterprise.slackassistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    private final ChatClient chatClient;

    public LlmService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String chat(String userMessage) {
        return chat(userMessage, List.of());
    }

    public String chat(String userMessage, List<Map<String, String>> conversationHistory) {
        log.info("LLM request: '{}'", userMessage);

        String systemPrompt = buildSystemPrompt(conversationHistory);
        
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userMessage)
        ));

        ChatResponse response = chatClient.call(prompt);
        String content = response.getResult().getOutput().getContent();

        log.info("LLM response: '{}'", content);
        return content;
    }

    private String buildSystemPrompt(List<Map<String, String>> history) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
            You are a helpful assistant. Be concise and helpful.
            
            IMPORTANT GUIDELINES:
            1. If you believe you have resolved the user's issue, ask them to confirm: "Does this resolve your issue?"
            2. If the user says thanks/resolved/done, respond with a brief closing message.
            3. Keep track of the conversation context - user may switch between topics.
            4. Be proactive in asking if the user needs anything else.
            """);

        if (!history.isEmpty()) {
            sb.append("\n\n=== CONVERSATION HISTORY ===\n");
            for (Map<String, String> msg : history) {
                sb.append(msg.get("role")).append(": ").append(msg.get("content")).append("\n");
            }
            sb.append("=== END HISTORY ===\n");
        }

        return sb.toString();
    }
}
