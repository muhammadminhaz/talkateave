package com.muhammadminhaz.talkateeve.validation;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileUploadValidatorTests {

    private final FileUploadValidator validator = new FileUploadValidator();

    private MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("files", name, contentType, content);
    }

    @Test
    void validateFile_acceptsPlainTextFile() {
        MultipartFile ok = file("notes.txt", "text/plain", "hello".getBytes());

        assertDoesNotThrow(() -> validator.validateFile(ok));
    }

    @Test
    void validateFile_rejectsEmptyFile() {
        MultipartFile empty = file("empty.txt", "text/plain", new byte[0]);

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> validator.validateFile(empty));
        assertEquals("File is empty", ex.getMessage());
    }

    @Test
    void validateFile_rejectsOversizedFile() {
        MultipartFile big = file("big.txt", "text/plain", new byte[11 * 1024 * 1024]);

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> validator.validateFile(big));
        assertTrue(ex.getMessage().contains("exceeds maximum limit"), ex.getMessage());
    }

    @Test
    void validateFile_rejectsUnsupportedContentType() {
        MultipartFile exe = file("virus.exe", "application/x-msdownload", "MZ".getBytes());

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> validator.validateFile(exe));
        assertTrue(ex.getMessage().contains("Unsupported file type"), ex.getMessage());
    }

    @Test
    void validateFile_rejectsNullContentType() {
        MultipartFile noType = file("notes.txt", null, "hello".getBytes());

        assertThrows(IllegalArgumentException.class, () -> validator.validateFile(noType));
    }

    @Test
    void validateFile_rejectsPathTraversalFilename() {
        MultipartFile traversal = file("../../etc/passwd", "text/plain", "root".getBytes());

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> validator.validateFile(traversal));
        assertEquals("Invalid filename", ex.getMessage());
    }

    @Test
    void validateFiles_rejectsNullOrEmptyList() {
        assertThrows(IllegalArgumentException.class, () -> validator.validateFiles(null));
        assertThrows(IllegalArgumentException.class, () -> validator.validateFiles(List.of()));
    }

    @Test
    void validateFiles_rejectsMoreThanTenFiles() {
        List<MultipartFile> many = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            many.add(file("f" + i + ".txt", "text/plain", "x".getBytes()));
        }

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> validator.validateFiles(many));
        assertTrue(ex.getMessage().contains("Maximum 10 files"), ex.getMessage());
    }

    @Test
    void validateFiles_rejectsTotalSizeOverFiftyMegabytes() {
        List<MultipartFile> files = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            files.add(file("f" + i + ".txt", "text/plain", new byte[9 * 1024 * 1024]));
        }

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> validator.validateFiles(files));
        assertTrue(ex.getMessage().contains("Total file size"), ex.getMessage());
    }

    @Test
    void validateFiles_acceptsValidBatch() {
        List<MultipartFile> files = List.of(
                file("a.txt", "text/plain", "a".getBytes()),
                file("b.pdf", "application/pdf", "%PDF".getBytes())
        );

        assertDoesNotThrow(() -> validator.validateFiles(files));
    }
}
