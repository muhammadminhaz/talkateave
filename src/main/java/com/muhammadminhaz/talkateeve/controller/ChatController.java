package com.muhammadminhaz.talkateeve.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.muhammadminhaz.talkateeve.dto.UserChatRequest;
import com.muhammadminhaz.talkateeve.dto.UserChatResponse;
import com.muhammadminhaz.talkateeve.service.ChatService;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ChatController {


    private final ChatService chatService;
    private final OllamaChatModel ollamaChatModel;

    public ChatController(ChatService chatService, OllamaChatModel ollamaChatModel) {
        this.chatService = chatService;
        this.ollamaChatModel = ollamaChatModel;
    }


    @PostMapping("/chat")
    public UserChatResponse chat(@RequestBody UserChatRequest request) {
        String botReply = chatService.getChatResponse(request.getMessage());
        return new UserChatResponse(botReply);
    }

    @PostMapping("/add-text")
    public ResponseEntity<String> addText(@RequestBody Map<String, String> payload) {
        String text = payload.get("message");
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().body("Content is required");
        }

        // Extract structured info from text
        Map<String, Object> metadata = extractProductInfo(text);

        // Add to vector store
        chatService.addKnowledge(text, metadata);

        return ResponseEntity.ok("Text added to RAG successfully");
    }

    public Map extractProductInfo(String text) {
        String prompt = """
        Extract product information from the following text in JSON format with keys: name, price, description, category.
        Text: "%s"
        JSON:
        """.formatted(text);

        Prompt aiPrompt = new Prompt(prompt);

        ChatResponse response = ollamaChatModel.call(aiPrompt);
        AssistantMessage output = response.getResult().getOutput();
        String json = output.getText();

        // Convert AI JSON string to Map
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            e.printStackTrace();
            // fallback: just store raw text
            return Map.of("content", text);
        }
    }




    // DTO for response

}

