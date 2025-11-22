package com.muhammadminhaz.talkateeve.controller;

import com.muhammadminhaz.talkateeve.service.BotDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/bots/{botId}/documents")
@RequiredArgsConstructor
public class BotDocumentController {

    private final BotDocumentService botDocumentService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listFiles(@PathVariable UUID botId) {
        return ResponseEntity.ok(botDocumentService.listBotFiles(botId));
    }

    @DeleteMapping("/{filename}")
    public ResponseEntity<Void> deleteFile(@PathVariable UUID botId, @PathVariable String filename) {
        botDocumentService.deleteFile(botId, filename);
        return ResponseEntity.noContent().build();
    }
}