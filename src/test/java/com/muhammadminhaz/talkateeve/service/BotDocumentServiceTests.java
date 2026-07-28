package com.muhammadminhaz.talkateeve.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.muhammadminhaz.talkateeve.model.Bot;
import com.muhammadminhaz.talkateeve.model.BotDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BotDocumentServiceTests {

    @Mock
    private VectorStore vectorStore;
    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private BotDocumentService service;
    private Bot bot;

    @BeforeEach
    void setUp() {
        // Real ObjectMapper: the cache round-trip is part of what we are testing.
        service = new BotDocumentService(vectorStore, embeddingService, redisTemplate,
                new ObjectMapper(), jdbcTemplate);

        bot = new Bot();
        bot.setId(UUID.randomUUID());
        bot.setName("Test Bot");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private MultipartFile textFile(String name, String content) {
        return new MockMultipartFile("files", name, "text/plain", content.getBytes());
    }

    @Test
    void uploadDocuments_persistsChunksAndPushesToVectorStore() throws Exception {
        when(embeddingService.createEmbedding(anyString())).thenReturn(new float[]{0.1f, 0.2f});

        List<BotDocument> saved = service.uploadDocuments(bot,
                List.of(textFile("kb.txt", "Support hours are 9am to 5pm.")));

        assertFalse(saved.isEmpty(), "expected at least one persisted chunk");
        assertEquals("kb.txt", saved.getFirst().getFilename());
        verify(jdbcTemplate, atLeastOnce()).update(contains("INSERT INTO bot_document"),
                any(), any(), any(), any(), any());
        verify(vectorStore, atLeastOnce()).add(anyList());
    }

    @Test
    void uploadDocuments_throwsWhenEmbeddingFails() {
        // Regression test for the live outage: a retired embedding model used to leave
        // uploadDocuments returning an empty list with HTTP 200 and no trace in the logs.
        when(embeddingService.createEmbedding(anyString()))
                .thenThrow(new RuntimeException("404 model not found"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.uploadDocuments(bot, List.of(textFile("kb.txt", "some content"))));

        assertTrue(ex.getMessage().contains("Document upload failed"), ex.getMessage());
        assertTrue(ex.getMessage().contains("kb.txt"), ex.getMessage());
        verify(vectorStore, never()).add(anyList());
    }

    @Test
    void uploadDocuments_reportsOversizedFileAsFailedRatherThanSkippingSilently() {
        byte[] tooBig = new byte[6 * 1024 * 1024]; // MAX_FILE_SIZE is 5MB
        MultipartFile big = new MockMultipartFile("files", "big.txt", "text/plain", tooBig);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.uploadDocuments(bot, List.of(big)));

        assertTrue(ex.getMessage().contains("big.txt"), ex.getMessage());
    }

    @Test
    void querySimilar_returnsCachedDocumentsOnHit() {
        String cached = """
                [{"id":"doc-1","content":"cached answer","metadata":{"bot_id":"b1"}}]""";
        when(valueOperations.get(anyString())).thenReturn(cached);

        List<Document> results = service.querySimilar(bot.getId().toString(), "hello", 3);

        assertEquals(1, results.size());
        assertEquals("cached answer", results.getFirst().getText());
        verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void querySimilar_searchesVectorStoreOnMissAndCachesResult() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("doc-1", "fresh answer", Map.of("bot_id", "b1"))));

        List<Document> results = service.querySimilar(bot.getId().toString(), "hello", 3);

        assertEquals(1, results.size());
        assertEquals("fresh answer", results.getFirst().getText());
        verify(valueOperations).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void querySimilar_evictsCorruptCacheEntryAndFallsThroughToSearch() {
        when(valueOperations.get(anyString())).thenReturn("{ this is not valid json");
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("doc-1", "fresh answer", Map.of())));

        List<Document> results = service.querySimilar(bot.getId().toString(), "hello", 3);

        assertEquals(1, results.size());
        verify(redisTemplate).delete(anyString());
        verify(vectorStore).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void querySimilar_propagatesVectorStoreFailure() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new RuntimeException("embedding model unavailable"));

        assertThrows(RuntimeException.class,
                () -> service.querySimilar(bot.getId().toString(), "hello", 3));
    }

    @Test
    void deleteDocument_wrapsAndRethrowsFailure() {
        UUID docId = UUID.randomUUID();
        when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db down"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.deleteDocument(docId));
        assertEquals("Document deletion failed", ex.getMessage());
        assertNotNull(ex.getCause(), "cause must be preserved so the stack trace survives");
    }

    @Test
    void deleteFile_isANoOpWhenBotHasNoMatchingChunks() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), any(), any()))
                .thenReturn(List.of());

        service.deleteFile(bot.getId(), "missing.txt");

        verify(vectorStore, never()).delete(anyList());
    }
}
