package com.enterprise.slackassistant.service;

import com.enterprise.slackassistant.config.ProductConfig;
import com.enterprise.slackassistant.config.ProductConfig.ProductDefinition;
import com.enterprise.slackassistant.exception.IntentNotFoundException;
import com.enterprise.slackassistant.model.IntentMapping;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and provides intent mappings for multiple products.
 * Each product has its own intent mapping file.
 */
@Service
public class IntentMappingService {

    private static final Logger log = LoggerFactory.getLogger(IntentMappingService.class);

    private final ObjectMapper objectMapper;
    private final ProductConfig productConfig;

    // productId -> (intentName -> IntentMapping)
    private final Map<String, Map<String, IntentMapping>> mappingsByProduct = new HashMap<>();

    public IntentMappingService(ObjectMapper objectMapper, ProductConfig productConfig) {
        this.objectMapper = objectMapper;
        this.productConfig = productConfig;
    }

    @PostConstruct
    public void loadMappings() {
        int totalMappings = 0;

        for (Map.Entry<String, ProductDefinition> entry : productConfig.getDefinitions().entrySet()) {
            String productId = entry.getKey();
            ProductDefinition definition = entry.getValue();
            String intentMappingFile = definition.getIntentMappingFile();

            if (intentMappingFile == null || intentMappingFile.isBlank()) {
                log.warn("IntentMappingService: no intent-mapping-file configured for product='{}'", productId);
                continue;
            }

            try {
                ClassPathResource resource = new ClassPathResource(intentMappingFile);
                try (InputStream is = resource.getInputStream()) {
                    List<IntentMapping> list = objectMapper.readValue(is,
                            new TypeReference<List<IntentMapping>>() {});

                    Map<String, IntentMapping> byIntent = new HashMap<>();
                    for (IntentMapping mapping : list) {
                        byIntent.put(mapping.getIntentName(), mapping);
                    }
                    mappingsByProduct.put(productId, byIntent);
                    totalMappings += list.size();

                    log.info("IntentMappingService: loaded {} intents for product='{}' from '{}'",
                            list.size(), productId, intentMappingFile);
                }
            } catch (IOException e) {
                log.error("IntentMappingService: failed to load intent mappings for product='{}' from '{}': {}",
                        productId, intentMappingFile, e.getMessage());
            }
        }

        log.info("IntentMappingService: initialized with {} total mappings for {} product(s)",
                totalMappings, mappingsByProduct.size());
    }

    /**
     * Looks up an intent mapping for a given product and intent name.
     *
     * @param productId  the product ID (e.g., "artemis", "b360")
     * @param intentName the detected intent name
     * @return the IntentMapping with API names to call
     * @throws IntentNotFoundException if no mapping exists
     */
    public IntentMapping lookup(String productId, String intentName) {
        Map<String, IntentMapping> byIntent = mappingsByProduct.get(productId);
        if (byIntent == null) {
            throw new IntentNotFoundException(productId, intentName);
        }

        // Exact match first
        IntentMapping exact = byIntent.get(intentName);
        if (exact != null) {
            log.info("         IntentMapping: EXACT match | product='{}' intent='{}' → apiNames={}",
                    productId, intentName, exact.getApiNames());
            return exact;
        }

        // Case-insensitive fallback
        log.info("         IntentMapping: no exact match, trying case-insensitive for intent='{}'", intentName);
        return byIntent.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(intentName))
                .map(e -> {
                    log.info("         IntentMapping: CASE-INSENSITIVE match | '{}' → apiNames={}",
                            e.getKey(), e.getValue().getApiNames());
                    return e.getValue();
                })
                .findFirst()
                .orElseThrow(() -> new IntentNotFoundException(productId, intentName));
    }

    /**
     * Gets all known intents for a specific product.
     */
    public List<String> getKnownIntents(String productId) {
        return List.copyOf(mappingsByProduct.getOrDefault(productId, Map.of()).keySet());
    }

    /**
     * Gets all known intents across all products.
     */
    public List<String> getAllKnownIntents() {
        return mappingsByProduct.values().stream()
                .flatMap(m -> m.keySet().stream())
                .distinct()
                .toList();
    }

    /**
     * Checks if a product has intent mappings loaded.
     */
    public boolean hasProduct(String productId) {
        return mappingsByProduct.containsKey(productId);
    }
}
