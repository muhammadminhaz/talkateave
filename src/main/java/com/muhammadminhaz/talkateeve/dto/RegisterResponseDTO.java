package com.muhammadminhaz.talkateeve.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponseDTO {
    private String message;
    private String email;
}
