package com.enterprise.slackassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Slack AI Support Assistant.
 *
 * <p>The application:
 * <ol>
 *   <li>Receives Slack messages via Bolt Jakarta Servlet at {@code /slack/events}</li>
 *   <li>Resolves the Artemis AppId from the Slack channel name</li>
 *   <li>Detects user intent with OpenAI GPT-4o</li>
 *   <li>Checks the in-memory response cache</li>
 *   <li>Dispatches to mock Artemis APIs and retrieves RAG knowledge in parallel</li>
 *   <li>Synthesises a final answer with OpenAI and posts it back to Slack</li>
 * </ol>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class SlackAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(SlackAssistantApplication.class, args);
    }
}





