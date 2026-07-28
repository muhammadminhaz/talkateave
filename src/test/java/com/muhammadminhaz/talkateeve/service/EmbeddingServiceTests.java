package com.muhammadminhaz.talkateeve.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTests {

    @Mock
    private EmbeddingModel embeddingModel;

    @InjectMocks
    private EmbeddingService embeddingService;

    @Test
    void createEmbedding_returnsFirstResultVector() {
        float[] vector = {0.1f, 0.2f, 0.3f};
        EmbeddingResponse response = new EmbeddingResponse(List.of(new Embedding(vector, 0)));
        when(embeddingModel.embedForResponse(anyList())).thenReturn(response);

        assertArrayEquals(vector, embeddingService.createEmbedding("hello"));
    }

    @Test
    void createEmbedding_propagatesModelFailure() {
        // A dead or retired embedding model must surface, never be swallowed.
        when(embeddingModel.embedForResponse(anyList()))
                .thenThrow(new RuntimeException("404 model not found: text-embedding-004"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> embeddingService.createEmbedding("hello"));
        assertTrue(ex.getMessage().contains("model not found"));
    }
}
