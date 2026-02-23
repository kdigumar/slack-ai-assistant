package com.enterprise.slackassistant.service;

import com.enterprise.slackassistant.config.ProductConfig;
import com.enterprise.slackassistant.config.ProductConfig.ProductDefinition;
import com.enterprise.slackassistant.dto.RagResult;
import com.enterprise.slackassistant.model.RagDocument;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * In-memory RAG service with per-product document stores.
 * Each product has its own set of RAG documents for context retrieval.
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final int TOP_N = 3;

    private final ObjectMapper objectMapper;
    private final ProductConfig productConfig;

    // productId -> List<RagDocument>
    private final Map<String, List<RagDocument>> documentsByProduct = new HashMap<>();

    public RagService(ObjectMapper objectMapper, ProductConfig productConfig) {
        this.objectMapper = objectMapper;
        this.productConfig = productConfig;
    }

    @PostConstruct
    public void loadDocuments() {
        int totalDocs = 0;

        for (Map.Entry<String, ProductDefinition> entry : productConfig.getDefinitions().entrySet()) {
            String productId = entry.getKey();
            ProductDefinition definition = entry.getValue();
            String ragDocsFile = definition.getRagDocsFile();

            if (ragDocsFile == null || ragDocsFile.isBlank()) {
                log.warn("RagService: no rag-docs-file configured for product='{}'", productId);
                documentsByProduct.put(productId, List.of()); // Empty list for products without RAG docs
                continue;
            }

            try {
                ClassPathResource resource = new ClassPathResource(ragDocsFile);
                try (InputStream is = resource.getInputStream()) {
                    List<RagDocument> docs = objectMapper.readValue(is,
                            new TypeReference<List<RagDocument>>() {});
                    documentsByProduct.put(productId, docs);
                    totalDocs += docs.size();

                    log.info("RagService: loaded {} documents for product='{}' from '{}'",
                            docs.size(), productId, ragDocsFile);
                }
            } catch (IOException e) {
                log.error("RagService: failed to load RAG docs for product='{}' from '{}': {}",
                        productId, ragDocsFile, e.getMessage());
                documentsByProduct.put(productId, List.of());
            }
        }

        log.info("RagService: initialized with {} total documents for {} product(s)",
                totalDocs, documentsByProduct.size());
    }

    /**
     * Retrieves relevant documents for a user query from a specific product's document store.
     *
     * @param productId the product ID to search within
     * @param userQuery the user's query text
     * @return list of matching RAG results, sorted by relevance
     */
    public List<RagResult> retrieve(String productId, String userQuery) {
        List<RagDocument> documents = documentsByProduct.getOrDefault(productId, List.of());

        if (documents.isEmpty()) {
            log.info("         RAG: no documents available for product='{}'", productId);
            return List.of();
        }

        String queryLower = userQuery.toLowerCase();
        Set<String> queryTokens = tokenise(queryLower);

        List<RagResult> scored = documents.stream()
                .map(doc -> score(doc, queryLower, queryTokens))
                .filter(r -> r.getScore() > 0)
                .sorted(Comparator.comparingDouble(RagResult::getScore).reversed())
                .limit(TOP_N)
                .toList();

        log.info("         RAG: retrieved {} doc(s) for product='{}' query='{}'",
                scored.size(), productId, userQuery);
        for (RagResult r : scored) {
            log.info("         RAG:   → doc='{}' score={}", r.getTitle(), String.format("%.2f", r.getScore()));
        }
        return scored;
    }

    /**
     * Legacy method for backward compatibility - searches all products.
     */
    public List<RagResult> retrieve(String userQuery) {
        List<RagResult> allResults = new ArrayList<>();

        for (String productId : documentsByProduct.keySet()) {
            allResults.addAll(retrieve(productId, userQuery));
        }

        return allResults.stream()
                .sorted(Comparator.comparingDouble(RagResult::getScore).reversed())
                .limit(TOP_N)
                .toList();
    }

    private RagResult score(RagDocument doc, String queryLower, Set<String> queryTokens) {
        long tokenMatches = doc.getKeywords().stream()
                .filter(kw -> queryTokens.contains(kw.toLowerCase()))
                .count();

        long substringMatches = doc.getKeywords().stream()
                .filter(kw -> kw.contains(" ") && queryLower.contains(kw.toLowerCase()))
                .count();

        double matched = tokenMatches + substringMatches;
        double score = matched / Math.max(queryTokens.size(), 1.0);

        return RagResult.of(doc.getId(), doc.getTitle(), doc.getContent(), Math.min(score, 1.0));
    }

    private Set<String> tokenise(String text) {
        return Arrays.stream(text.split("[\\s\\p{Punct}]+"))
                .filter(t -> !t.isBlank())
                .collect(Collectors.toSet());
    }

    /**
     * Gets all document titles for a product (for debugging).
     */
    public List<String> getDocumentTitles(String productId) {
        return documentsByProduct.getOrDefault(productId, List.of())
                .stream()
                .map(RagDocument::getTitle)
                .toList();
    }
}
