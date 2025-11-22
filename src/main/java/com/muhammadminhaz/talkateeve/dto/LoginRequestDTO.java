package com.muhammadminhaz.talkateeve.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequestDTO {
    @NotBlank(message = "Please provide a email")
    @Email
    private String email;
    @NotBlank(message = "Please provide a password")
    @Size(min = 4, message = "Password must be at least 4 characters long")
    private String password;
}
