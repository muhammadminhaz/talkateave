package com.muhammadminhaz.talkateeve.controller;

import com.muhammadminhaz.talkateeve.service.BotDocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class BotDocumentControllerTests {

    @Mock
    private BotDocumentService botDocumentService;

    private MockMvc mockMvc;
    private UUID botId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BotDocumentController(botDocumentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        botId = UUID.randomUUID();
    }

    @Test
    void listFiles_returnsFiles() throws Exception {
        when(botDocumentService.listBotFiles(botId))
                .thenReturn(List.of(Map.of("filename", "kb.txt", "chunks", 3)));

        mockMvc.perform(get("/api/bots/" + botId + "/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].filename").value("kb.txt"));
    }

    @Test
    void listFiles_returnsEmptyArray_whenBotHasNoDocuments() throws Exception {
        when(botDocumentService.listBotFiles(botId)).thenReturn(List.of());

        mockMvc.perform(get("/api/bots/" + botId + "/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listFiles_returns500WithErrorId_whenServiceFails() throws Exception {
        when(botDocumentService.listBotFiles(botId)).thenThrow(new RuntimeException("db down"));

        mockMvc.perform(get("/api/bots/" + botId + "/documents"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorId").exists());
    }

    @Test
    void deleteFile_returns204() throws Exception {
        mockMvc.perform(delete("/api/bots/" + botId + "/documents/kb.txt"))
                .andExpect(status().isNoContent());

        verify(botDocumentService).deleteFile(botId, "kb.txt");
    }

    @Test
    void deleteFile_returns500WithErrorId_whenServiceFails() throws Exception {
        doThrow(new RuntimeException("vector store unavailable"))
                .when(botDocumentService).deleteFile(botId, "kb.txt");

        mockMvc.perform(delete("/api/bots/" + botId + "/documents/kb.txt"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorId").exists());
    }
}
