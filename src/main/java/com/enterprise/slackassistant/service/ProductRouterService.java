package com.enterprise.slackassistant.service;

import com.enterprise.slackassistant.config.ProductConfig;
import com.enterprise.slackassistant.config.ProductConfig.ProductDefinition;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Routes Slack channel names to their corresponding product configuration.
 * This is the central place for multi-product support.
 */
@Service
public class ProductRouterService {

    private static final Logger log = LoggerFactory.getLogger(ProductRouterService.class);

    private final ProductConfig productConfig;

    // Reverse lookup: channelName -> productId
    private final Map<String, String> channelToProductId = new HashMap<>();

    // Direct lookup: productId -> ProductDefinition
    private final Map<String, ProductDefinition> productDefinitions = new HashMap<>();

    public ProductRouterService(ProductConfig productConfig) {
        this.productConfig = productConfig;
    }

    @PostConstruct
    public void init() {
        for (Map.Entry<String, ProductDefinition> entry : productConfig.getDefinitions().entrySet()) {
            String productId = entry.getKey();
            ProductDefinition definition = entry.getValue();
            productDefinitions.put(productId, definition);

            for (String channel : definition.getChannels()) {
                channelToProductId.put(channel.toLowerCase(), productId);
                log.info("ProductRouter: mapped channel='{}' → product='{}'", channel, productId);
            }
        }
        log.info("ProductRouter: initialized with {} product(s) and {} channel mapping(s)",
                productDefinitions.size(), channelToProductId.size());
    }

    /**
     * Resolves the product ID from a Slack channel name.
     *
     * @param channelName the Slack channel name (e.g., "artemishelp")
     * @return the product ID (e.g., "artemis")
     * @throws IllegalArgumentException if no product is configured for the channel
     */
    public String resolveProductId(String channelName) {
        String productId = channelToProductId.get(channelName.toLowerCase());
        if (productId == null) {
            log.warn("ProductRouter: no product found for channel='{}'. Configured channels: {}",
                    channelName, channelToProductId.keySet());
            throw new IllegalArgumentException(
                    "Unknown Slack channel '" + channelName + "'. Add it to products.*.channels in application.yml");
        }
        log.info("ProductRouter: channel='{}' → productId='{}'", channelName, productId);
        return productId;
    }

    /**
     * Gets the product definition by product ID.
     */
    public Optional<ProductDefinition> getProductDefinition(String productId) {
        return Optional.ofNullable(productDefinitions.get(productId));
    }

    /**
     * Gets the product definition for a channel name.
     */
    public Optional<ProductDefinition> getProductDefinitionByChannel(String channelName) {
        String productId = channelToProductId.get(channelName.toLowerCase());
        if (productId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(productDefinitions.get(productId));
    }

    /**
     * Returns the intent mapping file path for a product.
     */
    public String getIntentMappingFile(String productId) {
        ProductDefinition def = productDefinitions.get(productId);
        if (def == null) {
            throw new IllegalArgumentException("Unknown product: " + productId);
        }
        return def.getIntentMappingFile();
    }

    /**
     * Returns the RAG docs file path for a product.
     */
    public String getRagDocsFile(String productId) {
        ProductDefinition def = productDefinitions.get(productId);
        if (def == null) {
            throw new IllegalArgumentException("Unknown product: " + productId);
        }
        return def.getRagDocsFile();
    }

    /**
     * Returns the API base URL for a product.
     */
    public String getApiBaseUrl(String productId) {
        ProductDefinition def = productDefinitions.get(productId);
        if (def == null) {
            throw new IllegalArgumentException("Unknown product: " + productId);
        }
        return def.getApiBaseUrl();
    }

    /**
     * Returns all configured product IDs.
     */
    public java.util.Set<String> getAllProductIds() {
        return productDefinitions.keySet();
    }
}

