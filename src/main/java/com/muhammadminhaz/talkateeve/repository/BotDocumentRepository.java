package com.muhammadminhaz.talkateeve.repository;

import com.muhammadminhaz.talkateeve.model.BotDocument;
import com.pgvector.PGvector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BotDocumentRepository extends JpaRepository<BotDocument, UUID> {

    // Use PGvector type instead of float[]
    @Query(value = """
        SELECT * FROM bot_document
        WHERE bot_id = :botId
        ORDER BY embedding <-> CAST(:queryEmbedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<BotDocument> findSimilarDocuments(
            @Param("botId") UUID botId,
            @Param("queryEmbedding") String queryEmbedding,
            @Param("limit") int limit
    );

    // Alternative: Find by bot
    List<BotDocument> findByBotId(UUID botId);
}