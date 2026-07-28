package com.muhammadminhaz.talkateeve.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Slf4j
@Configuration
public class VectorStoreConfig {

    @Bean
    public EmbeddingModel embeddingModel(
            @Value("${spring.ai.google.genai.api-key}") String apiKey,
            @Value("${spring.ai.google.genai.embedding.text.options.model:gemini-embedding-001}") String model,
            @Value("${spring.ai.embedding.dimensions:768}") int dimensions) {

        // ponytail: DEFAULT_MODEL_NAME is text-embedding-004, which Google retired on
        // 2026-01-14 - never fall back to it. gemini-embedding-001 defaults to 3072 dims
        // but supports MRL truncation, so 768 keeps both the rag_documents and
        // bot_document schemas exactly as they are, with no migration.
        log.info("Embedding model={} dimensions={}", model, dimensions);

        GoogleGenAiEmbeddingConnectionDetails connectionDetails =
                GoogleGenAiEmbeddingConnectionDetails.builder()
                        .apiKey(apiKey)
                        .build();

        GoogleGenAiTextEmbeddingOptions options = GoogleGenAiTextEmbeddingOptions.builder()
                .model(model)
                .dimensions(dimensions)
                .taskType(GoogleGenAiTextEmbeddingOptions.TaskType.RETRIEVAL_DOCUMENT)
                .build();

        return new GoogleGenAiTextEmbeddingModel(connectionDetails, options);
    }

    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate,
                                   EmbeddingModel embeddingModel,
                                   @Value("${spring.ai.embedding.dimensions:768}") int dimensions) {
        // ponytail: MRL-truncated vectors are not unit length, but PgVectorStore's default
        // COSINE_DISTANCE is scale-invariant so similarity stays correct. The only L2 (<->)
        // consumer is BotDocumentRepository.findSimilarDocuments, which is unused.
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(dimensions)
                .initializeSchema(true)
                .schemaName("public")
                .vectorTableName("rag_documents")
                .maxDocumentBatchSize(10000)
                .build();
    }
}
