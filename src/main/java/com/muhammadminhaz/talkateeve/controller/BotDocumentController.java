package com.muhammadminhaz.talkateeve.controller;

import com.muhammadminhaz.talkateeve.service.BotDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/bots/{botId}/documents")
@RequiredArgsConstructor
public class BotDocumentController {

    private final BotDocumentService botDocumentService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listFiles(@PathVariable UUID botId) {
        List<Map<String, Object>> files = botDocumentService.listBotFiles(botId);
        log.debug("listFiles botId={} count={}", botId, files.size());
        return ResponseEntity.ok(files);
    }

    @DeleteMapping("/{filename}")
    public ResponseEntity<Void> deleteFile(@PathVariable UUID botId, @PathVariable String filename) {
        log.info("deleteFile botId={} filename={}", botId, filename);
        botDocumentService.deleteFile(botId, filename);
        return ResponseEntity.noContent().build();
    }
}