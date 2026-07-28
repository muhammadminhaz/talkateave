package com.muhammadminhaz.talkateeve.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Re-embeds stored chunks in place after an embedding model change.
 *
 * <p>Deliberately an UPDATE and not a TRUNCATE. Uploaded files are never persisted:
 * {@code BotDocumentService} chunks them in memory and keeps only the text in the
 * {@code content} column. That column is therefore the single surviving copy of every
 * user's knowledge base, and truncating either table would destroy it with nothing left
 * to rebuild from.
 *
 * <p>Both models produce 768-dimension vectors here, so no schema change is involved.
 */
@Slf4j
@Service
public class EmbeddingReindexService {

    /** Both tables are (id uuid, content text, embedding vector). */
    private static final List<String> TABLES = List.of("bot_document", "rag_documents");

    private static final int PAGE_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;

    public EmbeddingReindexService(JdbcTemplate jdbcTemplate, EmbeddingService embeddingService) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingService = embeddingService;
    }

    public record Result(String table, int reembedded, int failed) {
    }

    public List<Result> reindexAll() {
        return TABLES.stream().map(this::reindex).toList();
    }

    public Result reindex(String table) {
        if (!TABLES.contains(table)) {
            throw new IllegalArgumentException("Unknown table: " + table);
        }

        int reembedded = 0;
        int failed = 0;
        int offset = 0;

        while (true) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, content FROM " + table + " ORDER BY id LIMIT ? OFFSET ?",
                    PAGE_SIZE, offset);
            if (rows.isEmpty()) {
                break;
            }

            for (Map<String, Object> row : rows) {
                String id = String.valueOf(row.get("id"));
                String content = (String) row.get("content");
                if (content == null || content.isBlank()) {
                    continue;
                }
                try {
                    jdbcTemplate.update(
                            "UPDATE " + table + " SET embedding = ?::vector WHERE id = ?::uuid",
                            toVectorLiteral(embeddingService.createEmbedding(content)), id);
                    reembedded++;
                } catch (Exception e) {
                    // Keep going: one poisoned chunk must not strand the rest of the corpus
                    // on vectors from the retired model.
                    failed++;
                    log.error("Re-embedding failed for {} row {}", table, id, e);
                }
            }

            offset += rows.size();
            log.info("Re-embedded {} of {} so far ({} failed)", reembedded, table, failed);
        }

        log.info("Re-index of {} complete: {} updated, {} failed", table, reembedded, failed);
        return new Result(table, reembedded, failed);
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding[i]);
        }
        return sb.append(']').toString();
    }
}
