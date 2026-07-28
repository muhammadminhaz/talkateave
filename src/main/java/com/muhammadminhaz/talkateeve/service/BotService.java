package com.muhammadminhaz.talkateeve.service;

import com.muhammadminhaz.talkateeve.dto.BotRequest;
import com.muhammadminhaz.talkateeve.dto.BotResponse;
import com.muhammadminhaz.talkateeve.dto.ChatMessageDTO;
import com.muhammadminhaz.talkateeve.dto.DashboardStatsResponse;
import com.muhammadminhaz.talkateeve.model.Bot;
import com.muhammadminhaz.talkateeve.model.BotQuery;
import com.muhammadminhaz.talkateeve.model.User;
import com.muhammadminhaz.talkateeve.repository.BotQueryRepository;
import com.muhammadminhaz.talkateeve.repository.BotRepository;
import com.muhammadminhaz.talkateeve.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BotService {

    private final GoogleGenAiChatModel chatModel;
    private final BotRepository botRepository;
    private final UserRepository userRepository;
    private final BotDocumentService botDocumentService;
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final BotQueryRepository botQueryRepository;

    public BotService(GoogleGenAiChatModel chatModel,
                      BotRepository botRepository,
                      UserRepository userRepository,
                      BotDocumentService botDocumentService,
                      JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                      BotQueryRepository botQueryRepository) {
        this.chatModel = chatModel;
        this.botRepository = botRepository;
        this.userRepository = userRepository;
        this.botDocumentService = botDocumentService;
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.botQueryRepository = botQueryRepository;
    }

    /**
     * Create a bot and optionally train it with uploaded files
     */
    public BotResponse createBot(BotRequest request, UUID userId, List<MultipartFile> files) throws Exception {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Bot bot = new Bot();
        bot.setName(request.getName());
        bot.setDescription(request.getDescription());
        bot.setSlug(generateSlug(request.getName()));
        bot.setInstructions(request.getInstructions());
        bot.setUser(user);

        Bot savedBot = botRepository.save(bot);

        if (files != null && !files.isEmpty()) {
            botDocumentService.uploadDocuments(savedBot, files);
        }

        return BotResponse.fromBot(savedBot);
    }

    /**
     * Update bot metadata and instructions
     */
    public BotResponse updateBot(UUID botId, BotRequest request, UUID userId, List<MultipartFile> files) throws Exception {
        Bot bot = botRepository.findById(botId)
                .orElseThrow(() -> new RuntimeException("Bot not found"));

        if (!bot.getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized to update this bot");
        }

        bot.setName(request.getName());
        bot.setDescription(request.getDescription());
        bot.setSlug(generateSlug(request.getName()));
        bot.setInstructions(request.getInstructions());

        Bot updatedBot = botRepository.save(bot);

        if (files != null && !files.isEmpty()) {
            botDocumentService.uploadDocuments(updatedBot, files);
        }

        return BotResponse.fromBot(updatedBot);
    }

    /**
     * Get all bots for a user
     */
    public List<BotResponse> getUserBots(UUID userId) {
        return botRepository.findByUserId(userId)
                .stream()
                .map(BotResponse::fromBot)
                .collect(Collectors.toList());
    }

    /**
     * Get single bot details
     */
    public BotResponse getBot(UUID botId, UUID userId) {
        Bot bot = botRepository.findById(botId)
                .orElseThrow(() -> new RuntimeException("Bot not found"));

        if (!bot.getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized to view this bot");
        }

        return BotResponse.fromBot(bot);
    }

    /**
     * Delete a bot
     */
    public void deleteBot(UUID botId, UUID userId) {
        Bot bot = botRepository.findById(botId)
                .orElseThrow(() -> new RuntimeException("Bot not found"));

        if (!bot.getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized to delete this bot");
        }

        String sql = """
        WITH deleted_docs AS (
            DELETE FROM bot_document
            WHERE bot_id = :botId
            RETURNING id
        ),
        deleted_rag AS (
            DELETE FROM rag_documents
            WHERE id IN (SELECT id FROM deleted_docs)
        ),
        deleted_instructions AS (
            DELETE FROM bot_instructions
            WHERE bot_id = :botId
        )
        DELETE FROM bot
        WHERE id = :botId
    """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("botId", botId);

        namedParameterJdbcTemplate.update(sql, params);
    }

    /**
     * Ask bot with conversation history (for chat widget)
     */
    public String askBotWithHistory(UUID botId, String question, List<ChatMessageDTO> history) {
        Bot bot = botRepository.findById(botId)
                .orElseThrow(() -> new RuntimeException("Bot not found"));

        try {
            // Retrieve relevant documents from RAG
            List<org.springframework.ai.document.Document> docs = botDocumentService.querySimilar(
                    botId.toString(), question, 3
            );

            String context = docs.stream()
                    .map(org.springframework.ai.document.Document::getText)
                    .collect(Collectors.joining("\n\n"));

            String instructions = String.join("\n", bot.getInstructions());

            // Build conversation history
            StringBuilder conversationContext = new StringBuilder();
            if (history != null && !history.isEmpty()) {
                conversationContext.append("Previous conversation:\n");
                for (ChatMessageDTO msg : history) {
                    String role = "user".equals(msg.getRole()) ? "User" : "Assistant";
                    conversationContext.append(String.format("%s: %s\n", role, msg.getContent()));
                }
                conversationContext.append("\n");
            }

            String prompt = String.format("""
                You are a helpful assistant. Follow these instructions:
                %s
                
                %s
                
                Use the following knowledge base to answer questions:
                %s
                
                Current question: %s
                
                Provide a helpful, contextual answer based on the conversation history and knowledge base.
                If the answer isn't in the knowledge base, say so politely.
                """,
                    instructions,
                    conversationContext.toString(),
                    context.isEmpty() ? "No specific context available." : context,
                    question
            );

            String answer = chatModel.call(new Prompt(prompt))
                    .getResult()
                    .getOutput()
                    .getText();

            recordQuery(botId);
            return answer;

        } catch (Exception e) {
            // Returning a friendly string here made every failure look like a successful
            // 200 to the caller. Rethrow: GlobalExceptionHandler logs it with an errorId
            // and returns a 5xx, and widget.js already renders its own friendly message
            // on !response.ok.
            log.error("Bot query failed for bot {}", botId, e);
            throw new RuntimeException("Bot query failed for bot " + botId, e);
        }
    }

    /**
     * Ask bot without history (backward compatibility)
     */
    public String askBot(UUID botId, String question) {
        return askBotWithHistory(botId, question, new ArrayList<>());
    }

    /**
     * Get bot documents for display/editing
     */
    public List<Map<String, Object>> getBotDocuments(UUID botId, UUID userId) {
        Bot bot = botRepository.findById(botId)
                .orElseThrow(() -> new RuntimeException("Bot not found"));

        if (!bot.getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        String sql = """
            SELECT DISTINCT filename, COUNT(*) as chunk_count
            FROM bot_document
            WHERE bot_id = ?::uuid
            GROUP BY filename
            ORDER BY filename
            """;

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> Map.of(
                        "filename", rs.getString("filename"),
                        "chunkCount", rs.getInt("chunk_count")
                ),
                botId.toString()
        );
    }

    /**
     * Delete document by filename
     */
    public void deleteDocument(UUID botId, String filename, UUID userId) {
        Bot bot = botRepository.findById(botId)
                .orElseThrow(() -> new RuntimeException("Bot not found"));

        if (!bot.getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        String sql = "DELETE FROM bot_document WHERE bot_id = ?::uuid AND filename = ?";
        int deleted = jdbcTemplate.update(sql, botId.toString(), filename);

        log.info("Deleted {} chunks for file {} from bot {}", deleted, filename, botId);

        // Invalidate cache
        botDocumentService.invalidateBotCache(botId.toString());
    }

    /**
     * Counts one answered question. Best effort on purpose: a dashboard metric must
     * never be the reason a user's chat request fails.
     */
    private void recordQuery(UUID botId) {
        try {
            botQueryRepository.save(new BotQuery(botId));
        } catch (Exception e) {
            log.warn("Could not record query for bot {}", botId, e);
        }
    }

    /**
     * Real numbers for the dashboard Overview, which used to be hardcoded.
     */
    public DashboardStatsResponse getDashboardStats(UUID userId, int days) {
        List<Bot> bots = botRepository.findByUserId(userId);
        List<String> dayLabels = new ArrayList<>();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (int i = days - 1; i >= 0; i--) {
            dayLabels.add(today.minusDays(i).toString());
        }

        if (bots.isEmpty()) {
            return new DashboardStatsResponse(0, 0, 0, dayLabels, List.of());
        }

        List<UUID> botIds = bots.stream().map(Bot::getId).toList();
        long totalInteractions = botQueryRepository.countByBotIdIn(botIds);

        Long documents = namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT filename) FROM bot_document WHERE CAST(bot_id AS text) IN (:ids)",
                new MapSqlParameterSource("ids", botIds.stream().map(UUID::toString).toList()),
                Long.class);

        Instant since = today.minusDays(days - 1L).atStartOfDay(ZoneOffset.UTC).toInstant();
        Map<UUID, Map<String, Long>> byBot = new HashMap<>();
        for (Object[] row : botQueryRepository.countDailyByBot(botIds, since)) {
            byBot.computeIfAbsent((UUID) row[0], k -> new HashMap<>())
                    .put(row[1].toString(), ((Number) row[2]).longValue());
        }

        List<DashboardStatsResponse.BotSeries> series = bots.stream()
                .map(bot -> {
                    Map<String, Long> counts = byBot.getOrDefault(bot.getId(), Map.of());
                    List<Long> data = dayLabels.stream()
                            .map(day -> counts.getOrDefault(day, 0L))
                            .toList();
                    return new DashboardStatsResponse.BotSeries(
                            bot.getId().toString(), bot.getName(), data);
                })
                .toList();

        return new DashboardStatsResponse(bots.size(), totalInteractions,
                documents == null ? 0 : documents, dayLabels, series);
    }

    private String generateSlug(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}