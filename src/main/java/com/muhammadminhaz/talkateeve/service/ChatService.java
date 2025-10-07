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

    private final Map<String, List<String>> sessionHistory = new HashMap<>();
    private String lastSessionId = null;

    public ChatService(OllamaChatModel ollamaChatModel,
                       VectorStore vectorStore) {
        this.ollamaChatModel = ollamaChatModel;
        this.vectorStore = vectorStore;
    }

    public void addKnowledge(String content, Map<String, Object> metadata) {
        Document document = new Document(content, metadata);
        vectorStore.add(List.of(document));
    }

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

        List<Document> relevantDocs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(userMessage)
                        .topK(3)
                        .build()
        );

        StringBuilder contextPrompt = new StringBuilder();

        if (!relevantDocs.isEmpty()) {
            contextPrompt.append("Relevant Business Information:\n");
            for (Document doc : relevantDocs) {
                contextPrompt.append(doc.getText()).append("\n\n");

            }
            contextPrompt.append("---\n\n");
        }

        contextPrompt.append("Conversation History:\n");
        int startIdx = Math.max(0, history.size() - 5);
        for (int i = startIdx; i < history.size(); i++) {
            contextPrompt.append(history.get(i)).append("\n");
        }

        contextPrompt.append("\nSystem Instructions:\n")
                .append("You are a professional, friendly, and natural-sounding customer support assistant for an online store.\n")
                .append("You maintain short-term memory of recent interactions, so you continue conversations naturally without repeating greetings or context already known.\n")
                .append("\nPrimary Role:\n")
                .append("• Assist users with product-related questions: names, prices, descriptions, categories, and comparisons.\n")
                .append("• Understand user intent and reply with clear, relevant, and accurate information based on the given context.\n")
                .append("\nRules:\n")
                .append("1. Only greet politely at the beginning of a *new* conversation, never in follow-up messages.\n")
                .append("2. Respond directly to the user’s query with helpful and concise information.\n")
                .append("3. Never assume or fabricate product details — rely only on provided data or stored knowledge.\n")
                .append("4. If an item or detail is missing, say naturally: \"I'm sorry, I don’t have that information right now.\"\n")
                .append("5. When multiple relevant products exist, list them clearly with name, price, and a short description.\n")
                .append("6. Keep answers brief, friendly, and clear — like a human support professional, not a scripted bot.\n")
                .append("7. Stay focused on product and support-related topics. If the user goes off-topic, politely bring the conversation back.\n")
                .append("8. Maintain context continuity — remember what was recently discussed and respond appropriately without restarting tone or topic.\n")
                .append("9. Do not ask unnecessary questions or repeat information the user already knows.\n")
                .append("\nStyle:\n")
                .append("• Polite, confident, and conversational — just like a real customer support agent.\n")
                .append("• Use natural human phrasing (e.g., 'Sure!', 'Of course!', 'Here’s what I found for you.')\n")
                .append("• Avoid robotic or template-like responses.\n")
                .append("• End replies with short, helpful touches only when relevant (e.g., 'Would you like me to show similar options?').\n")
                .append("\nAssistant: ");



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