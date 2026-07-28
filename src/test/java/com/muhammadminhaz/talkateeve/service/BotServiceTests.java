package com.muhammadminhaz.talkateeve.service;

import com.muhammadminhaz.talkateeve.dto.BotRequest;
import com.muhammadminhaz.talkateeve.dto.BotResponse;
import com.muhammadminhaz.talkateeve.model.Bot;
import com.muhammadminhaz.talkateeve.model.User;
import com.muhammadminhaz.talkateeve.repository.BotRepository;
import com.muhammadminhaz.talkateeve.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BotServiceTests {

    @Mock
    private GoogleGenAiChatModel chatModel;
    @Mock
    private BotRepository botRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BotDocumentService botDocumentService;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private BotService botService;

    private User owner;
    private Bot bot;

    @BeforeEach
    void setUp() {
        botService = new BotService(chatModel, botRepository, userRepository,
                botDocumentService, jdbcTemplate, namedParameterJdbcTemplate);

        owner = new User();
        owner.setId(UUID.randomUUID());
        owner.setEmail("owner@example.com");

        bot = new Bot();
        bot.setId(UUID.randomUUID());
        bot.setName("Support Bot");
        bot.setUser(owner);
        bot.setInstructions(List.of("Be brief."));
    }

    private void stubChatReply(String reply) {
        ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
        when(chatModel.call(any(Prompt.class))).thenReturn(response);
    }

    @Test
    void createBot_persistsBotAndGeneratesSlug() throws Exception {
        BotRequest request = new BotRequest();
        request.setName("My Support Bot!!");
        request.setDescription("desc");
        request.setInstructions(List.of("Be nice."));

        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(botRepository.save(any(Bot.class))).thenAnswer(inv -> {
            Bot saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        BotResponse response = botService.createBot(request, owner.getId(), null);

        assertEquals("my-support-bot", response.getSlug());
        verify(botRepository).save(any(Bot.class));
        verify(botDocumentService, never()).uploadDocuments(any(), anyList());
    }

    @Test
    void createBot_throwsWhenUserMissing() {
        BotRequest request = new BotRequest();
        request.setName("Bot");
        UUID unknown = UUID.randomUUID();
        when(userRepository.findById(unknown)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> botService.createBot(request, unknown, null));
        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void updateBot_rejectsNonOwner() {
        when(botRepository.findById(bot.getId())).thenReturn(Optional.of(bot));
        BotRequest request = new BotRequest();
        request.setName("Hijacked");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> botService.updateBot(bot.getId(), request, UUID.randomUUID(), null));
        assertEquals("Unauthorized to update this bot", ex.getMessage());
        verify(botRepository, never()).save(any(Bot.class));
    }

    @Test
    void getBot_rejectsNonOwner() {
        when(botRepository.findById(bot.getId())).thenReturn(Optional.of(bot));

        assertThrows(RuntimeException.class, () -> botService.getBot(bot.getId(), UUID.randomUUID()));
    }

    @Test
    void deleteBot_rejectsNonOwner() {
        when(botRepository.findById(bot.getId())).thenReturn(Optional.of(bot));

        assertThrows(RuntimeException.class, () -> botService.deleteBot(bot.getId(), UUID.randomUUID()));
        verify(namedParameterJdbcTemplate, never()).update(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class));
    }

    @Test
    void askBotWithHistory_buildsPromptFromRetrievedContextAndReturnsAnswer() {
        when(botRepository.findById(bot.getId())).thenReturn(Optional.of(bot));
        when(botDocumentService.querySimilar(eq(bot.getId().toString()), eq("What are your hours?"), anyInt()))
                .thenReturn(List.of(new Document("d1", "Support hours are 9am to 5pm.", java.util.Map.of())));
        stubChatReply("We are open 9am to 5pm.");

        String answer = botService.askBotWithHistory(bot.getId(), "What are your hours?", List.of());

        assertEquals("We are open 9am to 5pm.", answer);

        org.mockito.ArgumentCaptor<Prompt> captor = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        String prompt = captor.getValue().getContents();
        assertTrue(prompt.contains("Support hours are 9am to 5pm."), "retrieved context missing from prompt");
        assertTrue(prompt.contains("What are your hours?"), "question missing from prompt");
        assertTrue(prompt.contains("Be brief."), "bot instructions missing from prompt");
    }

    @Test
    void askBotWithHistory_throwsWhenBotMissing() {
        UUID unknown = UUID.randomUUID();
        when(botRepository.findById(unknown)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> botService.askBotWithHistory(unknown, "hello", List.of()));
        assertEquals("Bot not found", ex.getMessage());
    }

    @Test
    void askBotWithHistory_throwsInsteadOfReturningFriendlyStringWhenRetrievalFails() {
        // The old behaviour returned "I'm sorry, I encountered an error..." with HTTP 200,
        // which is how a broken embedding model looked identical to a successful answer.
        when(botRepository.findById(bot.getId())).thenReturn(Optional.of(bot));
        when(botDocumentService.querySimilar(anyString(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("404 model not found"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> botService.askBotWithHistory(bot.getId(), "hello", List.of()));

        assertTrue(ex.getMessage().contains("Bot query failed"), ex.getMessage());
        assertNotNull(ex.getCause());
    }

    @Test
    void askBotWithHistory_throwsWhenChatModelFails() {
        when(botRepository.findById(bot.getId())).thenReturn(Optional.of(bot));
        when(botDocumentService.querySimilar(anyString(), anyString(), anyInt())).thenReturn(List.of());
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("gemini unavailable"));

        assertThrows(RuntimeException.class,
                () -> botService.askBotWithHistory(bot.getId(), "hello", List.of()));
    }

    @Test
    void getUserBots_mapsRepositoryResults() {
        when(botRepository.findByUserId(owner.getId())).thenReturn(List.of(bot));

        List<BotResponse> bots = botService.getUserBots(owner.getId());

        assertEquals(1, bots.size());
        assertEquals("Support Bot", bots.getFirst().getName());
    }
}
