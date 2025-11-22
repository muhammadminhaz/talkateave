package com.muhammadminhaz.talkateeve.validation;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

@Component
public class FileUploadValidator {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .docx
            "application/msword", // .doc
            "text/plain",
            "text/markdown"
    );

    public void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    String.format("File size exceeds maximum limit of %dMB", MAX_FILE_SIZE / 1024 / 1024)
            );
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Unsupported file type. Allowed: PDF, DOCX, DOC, TXT, MD"
            );
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.contains("..")) {
            throw new IllegalArgumentException("Invalid filename");
        }
    }

    public void validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("No files provided");
        }

        if (files.size() > 10) {
            throw new IllegalArgumentException("Maximum 10 files allowed per upload");
        }

        long totalSize = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (totalSize > 50 * 1024 * 1024) { // 50MB total
            throw new IllegalArgumentException("Total file size exceeds 50MB");
        }

        files.forEach(this::validateFile);
    }
}