package com.muhammadminhaz.talkateeve.dto;

import com.muhammadminhaz.talkateeve.model.Bot;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class BotResponse {
    private UUID id;
    private String name;
    private String description;
    private String slug;
    private List<String> instructions;
    private String embedScript;

    public static BotResponse fromBot(Bot bot) {
        BotResponse response = new BotResponse();
        response.setId(bot.getId());
        response.setName(bot.getName());
        response.setDescription(bot.getDescription());
        response.setSlug(bot.getSlug());
        response.setInstructions(bot.getInstructions());
        response.setEmbedScript(generateEmbedScript(bot));
        return response;
    }

    /**
     * Base URL the widget is served from. Overridable so the embed snippet is correct in
     * every environment; defaults to the production backend.
     */
    private static final String WIDGET_BASE_URL = System.getenv()
            .getOrDefault("WIDGET_BASE_URL", "https://talkateave.158.178.146.95.sslip.io");

    private static String generateEmbedScript(Bot bot) {
        // data-bot-id must be the bare UUID: widget.js passes it straight to
        // ?botId=, and the server does UUID.fromString on it. Emitting "{slug}-{id}"
        // here made every copy-pasted embed fail with a 500 on the first message.
        return String.format(
                "<script src=\"%s/api/bots/widget.js\" data-bot-id=\"%s\" data-api-url=\"%s\"></script>",
                WIDGET_BASE_URL,
                bot.getId(),
                WIDGET_BASE_URL
        );
    }
}
