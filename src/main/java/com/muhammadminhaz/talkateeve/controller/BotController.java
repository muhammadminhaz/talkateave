package com.muhammadminhaz.talkateeve.controller;

import com.muhammadminhaz.talkateeve.dto.BotRequest;
import com.muhammadminhaz.talkateeve.dto.BotResponse;
import com.muhammadminhaz.talkateeve.model.User;
import com.muhammadminhaz.talkateeve.service.AuthService;
import com.muhammadminhaz.talkateeve.service.BotService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bots")
public class BotController {

    private final BotService botService;
    private final AuthService authService;

    public BotController(BotService botService, AuthService authService) {
        this.botService = botService;
        this.authService = authService;
    }

    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<BotResponse> createBot(
            @CookieValue(value = "token", required = false) String token,
            @RequestPart("request") BotRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) throws Exception {
        if (token == null || !authService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID userId = authService.getUserFromToken(token).getId();
        BotResponse response = botService.createBot(request, userId, files);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


    @PutMapping(value = "/{botId}", consumes = {"multipart/form-data"})
    public ResponseEntity<BotResponse> updateBot(
            @PathVariable UUID botId,
            @RequestPart("request") BotRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @CookieValue(value = "token", required = false) String token
    ) throws Exception {
        if (token == null || !authService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID userId = authService.getUserFromToken(token).getId();
        BotResponse response = botService.updateBot(botId, request, userId, files);
        return ResponseEntity.ok(response);
    }


    @GetMapping
    public ResponseEntity<List<BotResponse>> getUserBots(@CookieValue(value = "token", required = false) String token) {
        if (token == null || !authService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UUID userId = authService.getUserFromToken(token).getId();
        List<BotResponse> bots = botService.getUserBots(userId);
        return ResponseEntity.ok(bots);
    }

    @GetMapping("/{botId}")
    public ResponseEntity<BotResponse> getBot(
            @PathVariable UUID botId,
            @CookieValue(value = "token", required = false) String token) {
        if (token == null || !authService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UUID userId = authService.getUserFromToken(token).getId();
        BotResponse response = botService.getBot(botId, userId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{botId}")
    public ResponseEntity<Void> deleteBot(
            @PathVariable UUID botId,
            @CookieValue(value = "token", required = false) String token) {
        if (token == null || !authService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UUID userId = authService.getUserFromToken(token).getId();
        botService.deleteBot(botId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{botId}/ask")
    public ResponseEntity<String> askBot(
            @PathVariable UUID botId,
            @RequestBody String question) {
        String answer = botService.askBot(botId, question);
        return ResponseEntity.ok(answer);
    }
}