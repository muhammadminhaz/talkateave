package com.muhammadminhaz.talkateeve.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmbeddingReindexServiceTests {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private EmbeddingService embeddingService;

    private EmbeddingReindexService service;

    @BeforeEach
    void setUp() {
        service = new EmbeddingReindexService(jdbcTemplate, embeddingService);
        when(embeddingService.createEmbedding(anyString())).thenReturn(new float[]{0.1f, 0.2f});
    }

    private void withRows(List<Map<String, Object>> rows) {
        when(jdbcTemplate.queryForList(anyString(), anyInt(), anyInt()))
                .thenReturn(rows)
                .thenReturn(List.of());
    }

    @Test
    void reindex_updatesInPlaceAndNeverDeletes() {
        // The content column is the only surviving copy of every uploaded document, so a
        // re-index that issues DELETE or TRUNCATE would destroy the corpus permanently.
        UUID id = UUID.randomUUID();
        withRows(List.of(Map.of("id", id, "content", "hello")));

        EmbeddingReindexService.Result result = service.reindex("bot_document");

        assertThat(result.reembedded()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        verify(jdbcTemplate).update(
                contains("UPDATE bot_document SET embedding"), eq("[0.1,0.2]"), eq(id.toString()));
        verify(jdbcTemplate, never()).update(contains("DELETE"), any(Object[].class));
        verify(jdbcTemplate, never()).execute(contains("TRUNCATE"));
    }

    @Test
    void reindex_continuesAfterARowFails() {
        withRows(List.of(
                Map.of("id", UUID.randomUUID(), "content", "bad"),
                Map.of("id", UUID.randomUUID(), "content", "good")));
        when(embeddingService.createEmbedding("bad")).thenThrow(new RuntimeException("quota"));

        EmbeddingReindexService.Result result = service.reindex("bot_document");

        assertThat(result.reembedded()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    void reindex_skipsBlankContentRatherThanEmbeddingIt() {
        withRows(List.of(Map.of("id", UUID.randomUUID(), "content", "   ")));

        assertThat(service.reindex("rag_documents").reembedded()).isZero();
        verify(embeddingService, never()).createEmbedding(anyString());
    }

    @Test
    void reindex_rejectsAnUnknownTable() {
        // The table name is concatenated into SQL, so the allow-list is the injection guard.
        assertThatThrownBy(() -> service.reindex("users; DROP TABLE bot_document"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbcTemplate);
    }
}
