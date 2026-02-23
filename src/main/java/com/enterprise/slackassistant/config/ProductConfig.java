package com.enterprise.slackassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for multi-product support.
 * Each product (Artemis, B360, Velocity) has its own configuration.
 */
@ConfigurationProperties(prefix = "products")
public class ProductConfig {

    private Map<String, ProductDefinition> definitions = new HashMap<>();

    public Map<String, ProductDefinition> getDefinitions() {
        return definitions;
    }

    public void setDefinitions(Map<String, ProductDefinition> definitions) {
        this.definitions = definitions;
    }

    /**
     * Defines a product with its associated channels, intent mappings, APIs, and RAG docs.
     */
    public static class ProductDefinition {
        private List<String> channels = List.of();
        private String intentMappingFile;
        private String apiBaseUrl;
        private String ragDocsFile;
        private long mockDelayMs = 50;

        public List<String> getChannels() {
            return channels;
        }

        public void setChannels(List<String> channels) {
            this.channels = channels;
        }

        public String getIntentMappingFile() {
            return intentMappingFile;
        }

        public void setIntentMappingFile(String intentMappingFile) {
            this.intentMappingFile = intentMappingFile;
        }

        public String getApiBaseUrl() {
            return apiBaseUrl;
        }

        public void setApiBaseUrl(String apiBaseUrl) {
            this.apiBaseUrl = apiBaseUrl;
        }

        public String getRagDocsFile() {
            return ragDocsFile;
        }

        public void setRagDocsFile(String ragDocsFile) {
            this.ragDocsFile = ragDocsFile;
        }

        public long getMockDelayMs() {
            return mockDelayMs;
        }

        public void setMockDelayMs(long mockDelayMs) {
            this.mockDelayMs = mockDelayMs;
        }

        @Override
        public String toString() {
            return "ProductDefinition{" +
                    "channels=" + channels +
                    ", intentMappingFile='" + intentMappingFile + '\'' +
                    ", apiBaseUrl='" + apiBaseUrl + '\'' +
                    ", ragDocsFile='" + ragDocsFile + '\'' +
                    '}';
        }
    }
}

