package com.muhammadminhaz.talkateeve.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserChatResponse {
    private String reply;
    public UserChatResponse(String reply) { this.reply = reply; }
}
