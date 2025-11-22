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

    private static String generateEmbedScript(Bot bot) {
        return String.format(
                "<script src=\"https://yourdomain.com/widget.js\" data-bot-id=\"%s-%s\"></script>",
                bot.getSlug(),
                bot.getId()
        );
    }
}
