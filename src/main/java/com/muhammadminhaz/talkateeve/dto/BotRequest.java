package com.muhammadminhaz.talkateeve.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class BotRequest {
    private UUID id;
    private String name;
    private String description;
    private String slug;
    private List<String> instructions;
}

