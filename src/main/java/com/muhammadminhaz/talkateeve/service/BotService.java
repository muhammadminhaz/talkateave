package com.muhammadminhaz.talkateeve.service;

import com.muhammadminhaz.talkateeve.dto.BotRequest;
import com.muhammadminhaz.talkateeve.dto.BotResponse;
import com.muhammadminhaz.talkateeve.model.Bot;
import com.muhammadminhaz.talkateeve.model.User;
import com.muhammadminhaz.talkateeve.repository.BotRepository;
import com.muhammadminhaz.talkateeve.repository.UserRepository;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BotService {

    private final GoogleGenAiChatModel chatModel;
    private final BotRepository botRepository;
    private final UserRepository userRepository;
    private final BotDocumentService botDocumentService;

    public BotService(GoogleGenAiChatModel chatModel,
                      BotRepository botRepository,
                      UserRepository userRepository,
                      BotDocumentService botDocumentService) {
        this.chatModel = chatModel;
        this.botRepository = botRepository;
        this.userRepository = userRepository;
        this.botDocumentService = botDocumentService;
    }

    /**
     * Create a bot and optionally train it with uploaded files
     */
    public BotResponse createBot(BotRequest request, UUID userId, List<MultipartFile> files) throws Exception {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Bot bot = new Bot();
        bot.setName(request.getName());
        bot.setDescription(request.getDescription());
        bot.setSlug(generateSlug(request.getName()));
        bot.setInstructions(request.getInstructions());
        bot.setUser(user);

        Bot savedBot = botRepository.save(bot);

        // Train bot with uploaded files
        if (files != null && !files.isEmpty()) {
            botDocumentService.uploadDocuments(savedBot, files);
        }

        return BotResponse.fromBot(savedBot);
    }

    /**
     * Update bot metadata and instructions
     */
    public BotResponse updateBot(UUID botId, BotRequest request, UUID userId, List<MultipartFile> files) throws Exception {
        Bot bot = botRepository.findById(botId)
                .orElseThrow(() -> new RuntimeException("Bot not found"));

        // Ownership check
        if (!bot.getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized to update this bot");
        }

        bot.setName(request.getName());
        bot.setDescription(request.getDescription());
        bot.setSlug(generateSlug(request.getName()));
        bot.setInstructions(request.getInstructions());

        Bot updatedBot = botRepository.save(bot);

        if (files != null && !files.isEmpty()) {
            botDocumentService.uploadDocuments(updatedBot, files);
        }

        return BotResponse.fromBot(updatedBot);
    }

    /**
     * Get all bots for a user
     */
    public List<BotResponse> getUserBots(UUID userId) {
        return botRepository.findByUserId(userId)
                .stream()
                .map(BotResponse::fromBot)
                .collect(Collectors.toList());
    }

    /**
     * Get single bot details
     */
    public BotResponse getBot(UUID botId, UUID userId) {
        Bot bot = botRepository.findById(botId)
                .orElseThrow(() -> new RuntimeException("Bot not found"));

        if (!bot.getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized to view this bot");
        }

        return BotResponse.fromBot(bot);
    }

    /**
     * Delete a bot
     */
    public void deleteBot(UUID botId, UUID userId) {
        Bot bot = botRepository.findById(botId)
                .orElseThrow(() -> new RuntimeException("Bot not found"));

        if (!bot.getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized to delete this bot");
        }

        botRepository.delete(bot);
    }

    /**
     * Improved askBot with error handling and context quality check
     */
    public String askBot(UUID botId, String question) {
        Bot bot = botRepository.findById(botId)
                .orElseThrow(() -> new RuntimeException("Bot not found"));

        try {
            // Retrieve relevant documents
            List<Document> docs = botDocumentService.querySimilar(
                    botId.toString(), question, 5
            );

            if (docs.isEmpty()) {
                return "I don't have enough information in my knowledge base to answer that question. Please upload relevant documents or rephrase your question.";
            }

            // Build context with relevance indicators
            String context = docs.stream()
                    .map(doc -> {
                        String source = (String) doc.getMetadata().get("filename");
                        return String.format("[Source: %s]\n%s", source, doc.getText());
                    })
                    .collect(Collectors.joining("\n\n---\n\n"));

            String instructions = String.join("\n", bot.getInstructions());

            String prompt = """
                You are a helpful assistant. Follow these instructions:
                %s
                
                Answer the question using ONLY the context below. If the answer isn't in the context, 
                say "I don't have that information in my knowledge base."
                
                Context:
                %s
                
                Question: %s
                
                Answer:
                """.formatted(instructions, context, question);

            return chatModel.call(new Prompt(prompt))
                    .getResult()
                    .getOutput()
                    .getText();

        } catch (Exception e) {
          //  log.error("Error during bot query: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process your question: " + e.getMessage());
        }
    }

    private String generateSlug(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}
