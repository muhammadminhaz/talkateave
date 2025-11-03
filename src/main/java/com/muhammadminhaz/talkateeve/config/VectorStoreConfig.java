package com.muhammadminhaz.talkateeve.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class VectorStoreConfig {

    @Bean
    public OllamaApi ollamaApi() {
        String ollamaBaseUrl = "http://localhost:11434";
        return OllamaApi.builder()
                .baseUrl(ollamaBaseUrl)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel(OllamaApi ollamaApi) {
        String embeddingModelName = "nomic-embed-text";
        OllamaOptions options = OllamaOptions.builder()
                .model(embeddingModelName)
                .build();

        return OllamaEmbeddingModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(options)
                .build();
    }

    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(768)
                .initializeSchema(true)
                .schemaName("public")
                .vectorTableName("rag_documents")
                .maxDocumentBatchSize(10000)
                .build();
    }
}
