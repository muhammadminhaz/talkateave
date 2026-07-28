package com.muhammadminhaz.talkateeve.controller;

import com.muhammadminhaz.talkateeve.dto.BotRequest;
import com.muhammadminhaz.talkateeve.dto.BotResponse;
import com.muhammadminhaz.talkateeve.dto.DashboardStatsResponse;
import com.muhammadminhaz.talkateeve.dto.ChatMessageDTO;
import com.muhammadminhaz.talkateeve.dto.ChatRequestDTO;
import com.muhammadminhaz.talkateeve.service.AuthService;
import com.muhammadminhaz.talkateeve.service.BotService;
import com.muhammadminhaz.talkateeve.validation.FileUploadValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/bots")
public class BotController {

    private final BotService botService;
    private final AuthService authService;
    private final FileUploadValidator fileUploadValidator;

    public BotController(BotService botService, AuthService authService,
                         FileUploadValidator fileUploadValidator) {
        this.botService = botService;
        this.authService = authService;
        this.fileUploadValidator = fileUploadValidator;
    }

    /**
     * Widget endpoint - Public API for embedded chat widgets
     * Accepts chat history for context-aware responses
     */
    @PostMapping("/widget/ask")
    public ResponseEntity<String> askBotWidget(
            @RequestParam String botId,
            @RequestBody ChatRequestDTO chatRequest
    ) {
        // No try/catch: GlobalExceptionHandler logs the stack trace with an errorId and
        // returns a 5xx. The old catch logged at INFO with no stack trace, which is why
        // a months-long outage left no trace in the logs.
        String question = chatRequest.getMessage();
        List<ChatMessageDTO> history = chatRequest.getHistory();

        log.info("widget ask botId={} historySize={}", botId, history == null ? 0 : history.size());

        String answer = botService.askBotWithHistory(
                UUID.fromString(botId),
                question,
                history
        );

        log.debug("widget answer botId={}: {}", botId, answer);
        return ResponseEntity.ok(answer);
    }

    /**
     * Serve the widget.js file
     */
    @GetMapping("/widget.js")
    public ResponseEntity<Resource> getWidgetJs() {
        Resource resource = new ClassPathResource("static/widget.js");
        return ResponseEntity.ok()
                .header("Content-Type", "application/javascript")
                .header("Cache-Control", "public, max-age=3600")
                .body(resource);
    }

    // ==================== AUTHENTICATED ENDPOINTS ====================

    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<BotResponse> createBot(
            @CookieValue(value = "token", required = false) String token,
            @RequestPart("request") BotRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) throws Exception {
        UUID userId = requireUserId(token);
        log.info("createBot user={} name={} files={}", userId, request.getName(), files == null ? 0 : files.size());
        if (files != null && !files.isEmpty()) {
            fileUploadValidator.validateFiles(files);
        }
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
        UUID userId = requireUserId(token);
        log.info("updateBot user={} botId={} files={}", userId, botId, files == null ? 0 : files.size());
        if (files != null && !files.isEmpty()) {
            fileUploadValidator.validateFiles(files);
        }
        BotResponse response = botService.updateBot(botId, request, userId, files);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsResponse> getDashboardStats(
            @CookieValue(value = "token", required = false) String token,
            @RequestParam(defaultValue = "7") int days
    ) {
        UUID userId = requireUserId(token);
        int window = Math.min(Math.max(days, 1), 90);
        return ResponseEntity.ok(botService.getDashboardStats(userId, window));
    }

    @GetMapping
    public ResponseEntity<List<BotResponse>> getUserBots(
            @CookieValue(value = "token", required = false) String token
    ) {
        UUID userId = requireUserId(token);
        List<BotResponse> bots = botService.getUserBots(userId);
        return ResponseEntity.ok(bots);
    }

    @GetMapping("/{botId}")
    public ResponseEntity<BotResponse> getBot(
            @PathVariable UUID botId,
            @CookieValue(value = "token", required = false) String token
    ) {
        UUID userId = requireUserId(token);
        BotResponse response = botService.getBot(botId, userId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{botId}")
    public ResponseEntity<Void> deleteBot(
            @PathVariable UUID botId,
            @CookieValue(value = "token", required = false) String token
    ) {
        UUID userId = requireUserId(token);
        log.info("deleteBot user={} botId={}", userId, botId);
        botService.deleteBot(botId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{botId}/ask")
    public ResponseEntity<String> askBot(
            @PathVariable UUID botId,
            @RequestBody Map<String, String> body,
            @CookieValue(value = "token", required = false) String token
    ) {
        requireUserId(token);

        String question = body.get("question");
        String answer = botService.askBot(botId, question);
        return ResponseEntity.ok(answer);
    }

    /**
     * Resolves the caller from the auth cookie. Throws 401 rather than returning it so
     * every endpoint shares one check: previously a valid token for a deleted user
     * NPE'd into a 500 at getUserFromToken(token).getId().
     */
    private UUID requireUserId(String token) {
        if (token == null || !authService.validateToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        var user = authService.getUserFromToken(token);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session no longer valid");
        }
        return user.getId();
    }
}
