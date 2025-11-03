package com.muhammadminhaz.talkateeve.dto;

import com.muhammadminhaz.talkateeve.validation.PasswordMatches;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@PasswordMatches
public class RegisterRequestDTO {

    @NotBlank(message = "Please provide a valid username")
    private String username;

    @NotBlank(message = "Please provide an email")
    @Email(message = "Please provide a valid email")
    private String email;

    @NotBlank(message = "Please provide a password")
    @Size(min = 4, message = "Password must be at least 4 characters long")
    private String password;

    @NotBlank(message = "Please confirm your password")
    @Size(min = 4, message = "Confirm password must be at least 4 characters long")
    private String confirmPassword;
}

