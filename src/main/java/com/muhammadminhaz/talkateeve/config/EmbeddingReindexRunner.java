package com.muhammadminhaz.talkateeve.config;

import com.muhammadminhaz.talkateeve.service.EmbeddingReindexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * One-shot re-index, off unless REINDEX_EMBEDDINGS=true is set for a single deploy.
 * It runs inside the deployed app because that is the only place holding both the
 * database credentials and the Gemini key.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.reindex-embeddings", havingValue = "true")
public class EmbeddingReindexRunner implements ApplicationRunner {

    private final EmbeddingReindexService reindexService;

    public EmbeddingReindexRunner(EmbeddingReindexService reindexService) {
        this.reindexService = reindexService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.warn("REINDEX_EMBEDDINGS is on: re-embedding every stored chunk. "
                + "Unset it after this deploy so the next restart does not repeat the work.");
        reindexService.reindexAll().forEach(result ->
                log.warn("Re-index result: table={} updated={} failed={}",
                        result.table(), result.reembedded(), result.failed()));
    }
}
