package com.muhammadminhaz.talkateeve.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.muhammadminhaz.talkateeve.dto.BotResponse;
import com.muhammadminhaz.talkateeve.dto.ChatRequestDTO;
import com.muhammadminhaz.talkateeve.model.Bot;
import com.muhammadminhaz.talkateeve.model.User;
import com.muhammadminhaz.talkateeve.service.AuthService;
import com.muhammadminhaz.talkateeve.service.BotService;
import com.muhammadminhaz.talkateeve.validation.FileUploadValidator;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BotControllerTests {

    @Mock
    private BotService botService;
    @Mock
    private AuthService authService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private User owner;
    private UUID botId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BotController(botService, authService, new FileUploadValidator()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        owner = new User();
        owner.setId(UUID.randomUUID());
        owner.setEmail("owner@example.com");
        botId = UUID.randomUUID();
    }

    private void authenticated() {
        when(authService.validateToken("good")).thenReturn(true);
        when(authService.getUserFromToken("good")).thenReturn(owner);
    }

    private BotResponse sampleResponse() {
        Bot bot = new Bot();
        bot.setId(botId);
        bot.setName("Support Bot");
        bot.setSlug("support-bot");
        bot.setUser(owner);
        return BotResponse.fromBot(bot);
    }

    @Test
    void widgetAsk_returnsAnswer() throws Exception {
        ChatRequestDTO request = new ChatRequestDTO();
        request.setMessage("hello");
        request.setHistory(List.of());

        when(botService.askBotWithHistory(eq(botId), eq("hello"), anyList())).thenReturn("Hi there!");

        mockMvc.perform(post("/api/bots/widget/ask")
                        .param("botId", botId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("Hi there!"));
    }

    @Test
    void widgetAsk_returns500WithErrorId_whenServiceFails() throws Exception {
        // The failure must reach the client as a 5xx with a correlatable errorId, not as
        // a 200 carrying an apology string.
        ChatRequestDTO request = new ChatRequestDTO();
        request.setMessage("hello");

        when(botService.askBotWithHistory(any(UUID.class), anyString(), any()))
                .thenThrow(new RuntimeException("embedding model unavailable"));

        mockMvc.perform(post("/api/bots/widget/ask")
                        .param("botId", botId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorId").exists());
    }

    @Test
    void widgetAsk_returns400_whenBotIdIsNotAUuid() throws Exception {
        ChatRequestDTO request = new ChatRequestDTO();
        request.setMessage("hello");

        mockMvc.perform(post("/api/bots/widget/ask")
                        .param("botId", "support-bot-not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(botService, never()).askBotWithHistory(any(), anyString(), any());
    }

    @Test
    void getUserBots_returns401_whenNoToken() throws Exception {
        mockMvc.perform(get("/api/bots"))
                .andExpect(status().isUnauthorized());
        verify(botService, never()).getUserBots(any());
    }

    @Test
    void getUserBots_returns401_whenTokenInvalid() throws Exception {
        when(authService.validateToken("bad")).thenReturn(false);

        mockMvc.perform(get("/api/bots").cookie(new Cookie("token", "bad")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getUserBots_returns401RatherThan500_whenUserNoLongerExists() throws Exception {
        when(authService.validateToken("good")).thenReturn(true);
        when(authService.getUserFromToken("good")).thenReturn(null);

        mockMvc.perform(get("/api/bots").cookie(new Cookie("token", "good")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getUserBots_returnsBots_whenAuthenticated() throws Exception {
        authenticated();
        when(botService.getUserBots(owner.getId())).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/bots").cookie(new Cookie("token", "good")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Support Bot"));
    }

    @Test
    void getBot_returnsEmbedScriptWithBareUuid() throws Exception {
        // Regression: the embed snippet used to carry "{slug}-{uuid}", which made every
        // copy-pasted widget 500 on its first message.
        authenticated();
        when(botService.getBot(botId, owner.getId())).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/bots/" + botId).cookie(new Cookie("token", "good")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embedScript")
                        .value(org.hamcrest.Matchers.containsString("data-bot-id=\"" + botId + "\"")));
    }

    @Test
    void deleteBot_returns204_whenAuthenticated() throws Exception {
        authenticated();

        mockMvc.perform(delete("/api/bots/" + botId).cookie(new Cookie("token", "good")))
                .andExpect(status().isNoContent());

        verify(botService).deleteBot(botId, owner.getId());
    }

    @Test
    void deleteBot_returns401_whenNoToken() throws Exception {
        mockMvc.perform(delete("/api/bots/" + botId))
                .andExpect(status().isUnauthorized());
        verify(botService, never()).deleteBot(any(), any());
    }

    @Test
    void askBot_returnsAnswer_whenAuthenticated() throws Exception {
        authenticated();
        when(botService.askBot(botId, "hello")).thenReturn("Hi!");

        mockMvc.perform(post("/api/bots/" + botId + "/ask")
                        .cookie(new Cookie("token", "good"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hi!"));
    }

    @Test
    void askBot_returns401_whenNoToken() throws Exception {
        mockMvc.perform(post("/api/bots/" + botId + "/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"hello\"}"))
                .andExpect(status().isUnauthorized());
        verify(botService, never()).askBot(any(), anyString());
    }

    @Test
    void createBot_rejectsDisallowedFileType_beforeTouchingTheService() throws Exception {
        // FileUploadValidator used to be dead code: nothing called it, so an .exe could
        // reach the RAG pipeline. This asserts it is actually on the request path.
        authenticated();

        MockMultipartFile request = new MockMultipartFile("request", "request", MediaType.APPLICATION_JSON_VALUE,
                "{\"name\":\"Bot\"}".getBytes());
        MockMultipartFile bad = new MockMultipartFile("files", "payload.exe",
                "application/x-msdownload", "MZ".getBytes());

        mockMvc.perform(multipart("/api/bots")
                        .file(request).file(bad)
                        .cookie(new Cookie("token", "good")))
                .andExpect(status().isBadRequest());

        verify(botService, never()).createBot(any(), any(), anyList());
    }

    @Test
    void createBot_acceptsAllowedFileType() throws Exception {
        authenticated();
        when(botService.createBot(any(), eq(owner.getId()), anyList())).thenReturn(sampleResponse());

        MockMultipartFile request = new MockMultipartFile("request", "request", MediaType.APPLICATION_JSON_VALUE,
                "{\"name\":\"Bot\"}".getBytes());
        MockMultipartFile ok = new MockMultipartFile("files", "kb.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/api/bots")
                        .file(request).file(ok)
                        .cookie(new Cookie("token", "good")))
                .andExpect(status().isCreated());
    }

    @Test
    void stats_returns401_whenNoToken() throws Exception {
        mockMvc.perform(get("/api/bots/stats"))
                .andExpect(status().isUnauthorized());
        verify(botService, never()).getDashboardStats(any(), anyInt());
    }

    @Test
    void stats_returnsRealNumbers_whenAuthenticated() throws Exception {
        // /api/bots/stats must win over /api/bots/{botId}, or "stats" reaches UUID.fromString.
        authenticated();
        when(botService.getDashboardStats(owner.getId(), 7)).thenReturn(
                new com.muhammadminhaz.talkateeve.dto.DashboardStatsResponse(
                        2, 42L, 3L, List.of("2026-07-28", "2026-07-29"),
                        List.of(new com.muhammadminhaz.talkateeve.dto.DashboardStatsResponse.BotSeries(
                                botId.toString(), "Support Bot", List.of(1L, 5L)))));

        mockMvc.perform(get("/api/bots/stats").cookie(new Cookie("token", "good")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInteractions").value(42))
                .andExpect(jsonPath("$.series[0].data[1]").value(5));
    }

    @Test
    void unknownUrl_returns404NotA500() throws Exception {
        // The catch-all handler used to log a full ERROR stack trace and return 500 for
        // every unknown path, which buries the failures the logging work exists to expose.
        mockMvc.perform(get("/api/bots/does/not/exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void malformedJsonBody_returns400NotA500() throws Exception {
        mockMvc.perform(post("/api/bots/widget/ask")
                        .param("botId", botId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void widgetJs_isServed() throws Exception {
        mockMvc.perform(get("/api/bots/widget.js"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/javascript"));
    }
}
