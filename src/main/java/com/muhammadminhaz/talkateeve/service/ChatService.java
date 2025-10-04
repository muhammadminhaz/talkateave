package com.muhammadminhaz.talkateeve.service;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ChatService {

    private final OllamaChatModel ollamaChatModel;
    private final VectorStore vectorStore;

    // Map of sessionId -> conversation history
    private final Map<String, List<String>> sessionHistory = new HashMap<>();
    private String lastSessionId = null;

    public ChatService(OllamaChatModel ollamaChatModel,
                       VectorStore vectorStore) {
        this.ollamaChatModel = ollamaChatModel;
        this.vectorStore = vectorStore;
    }

    /**
     * Add product/business knowledge to the vector store
     * @param content The business information (product name, price, description)
     * @param metadata Additional metadata (category, tags, etc.)
     */
    public void addKnowledge(String content, Map<String, Object> metadata) {
        Document document = new Document(content, metadata);
        vectorStore.add(List.of(document));
    }

    /**
     * Bulk add products
     */
    public void addProducts(List<ProductInfo> products) {
        List<Document> documents = new ArrayList<>();

        for (ProductInfo product : products) {
            String content = String.format(
                    "Product: %s\nPrice: %s\nDescription: %s\nCategory: %s",
                    product.getName(),
                    product.getPrice(),
                    product.getDescription(),
                    product.getCategory()
            );

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("type", "product");
            metadata.put("name", product.getName());
            metadata.put("price", product.getPrice());
            metadata.put("category", product.getCategory());

            documents.add(new Document(content, metadata));
        }

        vectorStore.add(documents);
    }

    /**
     * Handles chat messages with RAG enhancement
     */
    public String getChatResponse(String userMessage) {
        String sessionId;

        if (lastSessionId == null) {
            sessionId = UUID.randomUUID().toString();
            lastSessionId = sessionId;
        } else {
            sessionId = lastSessionId;
        }

        List<String> history = sessionHistory.getOrDefault(sessionId, new ArrayList<>());
        history.add("User: " + userMessage);

        // Retrieve relevant knowledge from vector store
        List<Document> relevantDocs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(userMessage)
                        .topK(3)
                        .build()
        );

        // Build context from retrieved documents
        StringBuilder contextPrompt = new StringBuilder();

        if (!relevantDocs.isEmpty()) {
            contextPrompt.append("Relevant Business Information:\n");
            for (Document doc : relevantDocs) {
                contextPrompt.append(doc.getText()).append("\n\n");

            }
            contextPrompt.append("---\n\n");
        }

        contextPrompt.append("Conversation History:\n");
        // Include last 5 messages for context (avoid token overflow)
        int startIdx = Math.max(0, history.size() - 5);
        for (int i = startIdx; i < history.size(); i++) {
            contextPrompt.append(history.get(i)).append("\n");
        }

        contextPrompt.append("\nSystem Instructions:\n")
                .append("You are a friendly and professional customer support assistant for an online store.\n")
                .append("Rules:\n")
                .append("1. Greet the user politely and professionally.\n")
                .append("2. Always provide accurate information about products, prices, and availability if the information exists.\n")
                .append("3. If you do not know the answer, respond politely: \"I'm sorry, I don't have that information right now.\"\n")
                .append("4. Use clear and concise language.\n")
                .append("5. Provide step-by-step instructions if needed.\n")
                .append("6. Avoid unnecessary opinions.\n")
                .append("7. Maintain a friendly and helpful tone.\n")
                .append("8. Confirm understanding when necessary.\n\n")
                .append("Assistant: ");


        Prompt prompt = new Prompt(contextPrompt.toString());

        ChatResponse response = ollamaChatModel.call(prompt);
        AssistantMessage output = response.getResult().getOutput();
        String botReply = output.getText();

        history.add("Assistant: " + botReply);
        sessionHistory.put(sessionId, history);

        return botReply;
    }

    /**
     * Search for specific products
     */
    public List<Document> searchProducts(String query, int topK) {
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .build()
        );
    }

    /**
     * Reset the current session
     */
    public void resetSession() {
        lastSessionId = null;
    }

    /**
     * Delete all knowledge from vector store (careful!)
     */
    public void clearKnowledge() {
        vectorStore.delete(List.of("*")); // Implementation depends on your VectorStore
    }

    // Inner class for product information
    public static class ProductInfo {
        private String name;
        private String price;
        private String description;
        private String category;

        public ProductInfo(String name, String price, String description, String category) {
            this.name = name;
            this.price = price;
            this.description = description;
            this.category = category;
        }

        // Getters
        public String getName() { return name; }
        public String getPrice() { return price; }
        public String getDescription() { return description; }
        public String getCategory() { return category; }
    }
}