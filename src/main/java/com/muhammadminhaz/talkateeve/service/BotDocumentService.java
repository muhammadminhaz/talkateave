package com.muhammadminhaz.talkateeve.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.muhammadminhaz.talkateeve.model.Bot;
import com.muhammadminhaz.talkateeve.model.BotDocument;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BotDocumentService {

    private final VectorStore vectorStore;
    private final EmbeddingService embeddingService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final Tika tika = new Tika();

    private static final String CACHE_PREFIX = "query:";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    // Memory management settings
    private static final int BATCH_SIZE = 5; // Process 5 chunks at a time
    private static final int MAX_CHUNK_SIZE = 500; // Smaller chunks
    private static final int CHUNK_OVERLAP = 100;
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB max per file
    private static final long MAX_TEXT_LENGTH = 1_000_000; // 1M chars max

    public BotDocumentService(VectorStore vectorStore,
                              EmbeddingService embeddingService,
                              RedisTemplate<String, String> redisTemplate,
                              ObjectMapper objectMapper,
                              JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Upload documents with streaming - NO full text in memory
     */
    @Transactional
    public List<BotDocument> uploadDocuments(Bot bot, List<MultipartFile> files) throws Exception {
        List<BotDocument> savedDocs = new ArrayList<>();

        for (MultipartFile file : files) {
            // Validate file size
            if (file.getSize() > MAX_FILE_SIZE) {
                log.warn("Skipping file {} - exceeds max size of {}MB",
                        file.getOriginalFilename(), MAX_FILE_SIZE / 1024 / 1024);
                continue;
            }

            log.info("Processing file: {} ({}KB)",
                    file.getOriginalFilename(),
                    file.getSize() / 1024);

            try {
                // Process file with streaming - never load full text
                List<BotDocument> fileDocs = processFileStreaming(bot, file);
                savedDocs.addAll(fileDocs);

                log.info("Processed {} chunks from {}", fileDocs.size(), file.getOriginalFilename());

                // Force cleanup
                System.gc();

            } catch (OutOfMemoryError e) {
                log.error("OUT OF MEMORY processing {}: {}", file.getOriginalFilename(), e.getMessage());
                System.gc();
                throw new RuntimeException("File too large - please use smaller file or increase heap size");
            } catch (Exception e) {
                log.error("Failed to process file {}: {}", file.getOriginalFilename(), e.getMessage());
            }
        }

        invalidateBotCache(bot.getId().toString());
        log.info("Successfully uploaded {} document chunks for bot {}", savedDocs.size(), bot.getId());

        return savedDocs;
    }

    /**
     * STREAMING APPROACH - Never holds full text in memory
     * Processes text line-by-line and creates chunks on the fly
     */
    private List<BotDocument> processFileStreaming(Bot bot, MultipartFile file) throws Exception {
        List<BotDocument> savedDocs = new ArrayList<>();
        List<Document> vectorDocsBatch = new ArrayList<>();

        String filename = file.getOriginalFilename();
        int chunkIndex = 0;

        // Use a streaming text chunker
        StreamingTextChunker chunker = new StreamingTextChunker(MAX_CHUNK_SIZE, CHUNK_OVERLAP);

        // Read file line by line
        String contentType = file.getContentType();

        if ("text/plain".equals(contentType)) {
            // For plain text, stream directly
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    chunker.addLine(line);

                    // Process any complete chunks
                    while (chunker.hasCompleteChunk()) {
                        String chunk = chunker.getNextChunk();

                        if (chunk != null && !chunk.isBlank()) {
                            BotDocument doc = processChunk(bot, filename, chunk, chunkIndex++, vectorDocsBatch);
                            if (doc != null) {
                                savedDocs.add(doc);
                            }

                            // Batch insert to vector store
                            if (vectorDocsBatch.size() >= BATCH_SIZE) {
                                insertVectorBatch(vectorDocsBatch);
                            }
                        }
                    }
                }

                // Process final chunk
                String finalChunk = chunker.getLastChunk();
                if (finalChunk != null && !finalChunk.isBlank()) {
                    BotDocument doc = processChunk(bot, filename, finalChunk, chunkIndex++, vectorDocsBatch);
                    if (doc != null) {
                        savedDocs.add(doc);
                    }
                }
            }

        } else {
            // For PDF/DOCX - extract to temp string (risky but necessary for Tika)
            // If this fails, file is too large
            String extractedText = null;
            try {
                extractedText = tika.parseToString(file.getInputStream());

                if (extractedText.length() > MAX_TEXT_LENGTH) {
                    log.warn("Extracted text too long ({}), truncating", extractedText.length());
                    extractedText = extractedText.substring(0, (int) MAX_TEXT_LENGTH);
                }

            } catch (OutOfMemoryError e) {
                log.error("File {} too large for Tika extraction", filename);
                throw new RuntimeException("PDF/DOCX file too large. Please reduce file size or convert to plain text.");
            }

            // Split extracted text into lines and stream through chunker
            if (extractedText != null) {
                String[] lines = extractedText.split("\n");
                extractedText = null; // Release immediately

                for (String line : lines) {
                    chunker.addLine(line);

                    while (chunker.hasCompleteChunk()) {
                        String chunk = chunker.getNextChunk();

                        if (chunk != null && !chunk.isBlank()) {
                            BotDocument doc = processChunk(bot, filename, chunk, chunkIndex++, vectorDocsBatch);
                            if (doc != null) {
                                savedDocs.add(doc);
                            }

                            if (vectorDocsBatch.size() >= BATCH_SIZE) {
                                insertVectorBatch(vectorDocsBatch);
                            }
                        }
                    }
                }

                // Final chunk
                String finalChunk = chunker.getLastChunk();
                if (finalChunk != null && !finalChunk.isBlank()) {
                    BotDocument doc = processChunk(bot, filename, finalChunk, chunkIndex++, vectorDocsBatch);
                    if (doc != null) {
                        savedDocs.add(doc);
                    }
                }
            }
        }

        // Insert remaining vector documents
        if (!vectorDocsBatch.isEmpty()) {
            insertVectorBatch(vectorDocsBatch);
        }

        return savedDocs;
    }

    /**
     * Process a single chunk - create embedding and save
     */
    private BotDocument processChunk(Bot bot, String filename, String chunk,
                                     int chunkIndex, List<Document> vectorBatch) {
        try {
            // Generate embedding
            float[] embedding = embeddingService.createEmbedding(chunk);

            // Insert to database
            UUID docId = UUID.randomUUID();
            insertDocument(docId, bot.getId(), filename, chunk, embedding);

            // Prepare for vector store
            Map<String, Object> metadata = Map.of(
                    "bot_id", bot.getId().toString(),
                    "filename", filename,
                    "chunk_index", chunkIndex
            );

            vectorBatch.add(new Document(docId.toString(), chunk, metadata));

            // Create entity for return
            return BotDocument.builder()
                    .id(docId)
                    .bot(bot)
                    .filename(filename)
                    .content(chunk)
                    .build();

        } catch (Exception e) {
            log.error("Failed to process chunk {}: {}", chunkIndex, e.getMessage());
            return null;
        }
    }

    /**
     * Insert batch of documents to vector store
     */
    private void insertVectorBatch(List<Document> batch) {
        if (batch.isEmpty()) return;

        try {
            vectorStore.add(new ArrayList<>(batch));
            log.debug("Inserted batch of {} documents to vector store", batch.size());
        } catch (Exception e) {
            log.error("Failed to insert vector batch: {}", e.getMessage());
        } finally {
            batch.clear();
        }
    }

    /**
     * Streaming text chunker - processes text without holding full content in memory
     */
    private static class StreamingTextChunker {
        private final int maxChunkSize;
        private final int overlap;
        private StringBuilder currentChunk = new StringBuilder();
        private String previousOverlap = "";

        public StreamingTextChunker(int maxChunkSize, int overlap) {
            this.maxChunkSize = maxChunkSize;
            this.overlap = overlap;
        }

        public void addLine(String line) {
            if (currentChunk.length() == 0 && !previousOverlap.isEmpty()) {
                currentChunk.append(previousOverlap);
            }

            currentChunk.append(line).append("\n");
        }

        public boolean hasCompleteChunk() {
            return currentChunk.length() >= maxChunkSize;
        }

        public String getNextChunk() {
            if (currentChunk.length() < maxChunkSize) {
                return null;
            }

            // Find good break point
            int breakPoint = findBreakPoint(currentChunk.toString(), maxChunkSize);

            String chunk = currentChunk.substring(0, breakPoint).trim();

            // Keep overlap for next chunk
            int overlapStart = Math.max(0, breakPoint - overlap);
            previousOverlap = currentChunk.substring(overlapStart, breakPoint);

            // Remove processed part
            currentChunk.delete(0, breakPoint);

            return chunk;
        }

        public String getLastChunk() {
            if (currentChunk.length() == 0) {
                return null;
            }
            return currentChunk.toString().trim();
        }

        private int findBreakPoint(String text, int target) {
            int end = Math.min(text.length(), target);

            // Try to break at sentence
            int lastPeriod = text.lastIndexOf('.', end);
            int lastNewline = text.lastIndexOf('\n', end);

            int breakPoint = Math.max(lastPeriod, lastNewline);

            if (breakPoint > target / 2) {
                return breakPoint + 1;
            }

            return end;
        }
    }

    /**
     * Insert document using native SQL
     */
    private void insertDocument(UUID docId, UUID botId, String filename,
                                String content, float[] embedding) {
        String vectorString = floatArrayToVectorString(embedding);

        String sql = "INSERT INTO bot_document(id, bot_id, filename, content, embedding) " +
                "VALUES (?::uuid, ?::uuid, ?, ?, ?::vector)";

        jdbcTemplate.update(sql,
                docId.toString(),
                botId.toString(),
                filename,
                content,
                vectorString
        );
    }

    @Transactional
    public void deleteDocument(UUID docId) {
        try {
            String sql = "DELETE FROM bot_document WHERE id = ?::uuid";
            jdbcTemplate.update(sql, docId.toString());
            vectorStore.delete(List.of(docId.toString()));
            log.info("Deleted document: {}", docId);
        } catch (Exception e) {
            log.error("Failed to delete document {}: {}", docId, e.getMessage());
            throw new RuntimeException("Document deletion failed", e);
        }
    }

    public List<Document> querySimilar(String botId, String query, int topK) {
        String cacheKey = generateCacheKey(botId, query, topK);

        String cachedResult = redisTemplate.opsForValue().get(cacheKey);
        if (cachedResult != null) {
            try {
                return deserializeDocuments(cachedResult);
            } catch (JsonProcessingException e) {
                redisTemplate.delete(cacheKey);
            }
        }

        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression(String.format("bot_id == '%s'", botId))
                .build();

        List<Document> results = vectorStore.similaritySearch(searchRequest);

        try {
            redisTemplate.opsForValue().set(cacheKey, serializeDocuments(results), CACHE_TTL);
        } catch (JsonProcessingException e) {
            log.error("Failed to cache query results: {}", e.getMessage());
        }

        return results;
    }

    private String floatArrayToVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            sb.append(embedding[i]);
            if (i < embedding.length - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private String generateCacheKey(String botId, String query, int topK) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(query.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return CACHE_PREFIX + botId + ":" + sb + ":" + topK;
        } catch (Exception e) {
            return CACHE_PREFIX + botId + ":" + query.hashCode() + ":" + topK;
        }
    }

    private void invalidateBotCache(String botId) {
        Set<String> keys = redisTemplate.keys(CACHE_PREFIX + botId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private String serializeDocuments(List<Document> documents) throws JsonProcessingException {
        List<Map<String, Object>> serializable = documents.stream()
                .map(doc -> Map.of(
                        "id", doc.getId(),
                        "content", doc.getText(),
                        "metadata", doc.getMetadata()
                ))
                .collect(Collectors.toList());
        return objectMapper.writeValueAsString(serializable);
    }

    private List<Document> deserializeDocuments(String json) throws JsonProcessingException {
        List<Map<String, Object>> maps = objectMapper.readValue(json, new TypeReference<>() {});
        return maps.stream()
                .map(map -> new Document(
                        (String) map.get("id"),
                        (String) map.get("content"),
                        (Map<String, Object>) map.get("metadata")
                ))
                .toList();
    }

    public List<Map<String, Object>> listBotFiles(UUID botId) {
        String sql = """
        SELECT filename, COUNT(*) AS chunk_count
        FROM bot_document
        WHERE bot_id = ?::uuid
        GROUP BY filename
        ORDER BY filename
    """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> Map.of(
                "filename", rs.getString("filename"),
                "chunkCount", rs.getInt("chunk_count")
        ), botId.toString());
    }

    @Transactional
    public void deleteFile(UUID botId, String filename) {
        String findSql = "SELECT id FROM bot_document WHERE bot_id = ?::uuid AND filename = ?";
        List<String> ids = jdbcTemplate.queryForList(findSql, String.class, botId.toString(), filename);

        if (ids.isEmpty()) return;

        String deleteSql = "DELETE FROM bot_document WHERE bot_id = ?::uuid AND filename = ?";
        jdbcTemplate.update(deleteSql, botId.toString(), filename);

        vectorStore.delete(ids);

        invalidateBotCache(botId.toString());

        log.info("Deleted file {} with {} chunks for bot {}", filename, ids.size(), botId);
    }

}