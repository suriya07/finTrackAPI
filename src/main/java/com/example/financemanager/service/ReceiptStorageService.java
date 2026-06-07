package com.example.financemanager.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/**
 * Stores transaction receipt images on the local filesystem under a configurable
 * directory ({@code app.receipts.dir}). One file per expense, named by the expense
 * id so re-uploads overwrite the prior receipt. Only image/PDF types are accepted.
 */
@Service
public class ReceiptStorageService {

    /** Allowed content types mapped to the extension used on disk. */
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/heic", "heic",
            "application/pdf", "pdf");

    private static final long MAX_BYTES = 10L * 1024 * 1024; // 10 MB

    private final Path root;

    public ReceiptStorageService(@Value("${app.receipts.dir:uploads/receipts}") String dir) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create receipts directory: " + root, e);
        }
    }

    /**
     * Persists {@code file} as the receipt for {@code expenseId}, replacing any
     * previously stored receipt for that expense. Returns the stored filename.
     */
    public String store(MultipartFile file, UUID expenseId) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Receipt file is empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Receipt exceeds 10 MB");
        }
        String ext = ALLOWED_TYPES.get(file.getContentType());
        if (ext == null) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Unsupported receipt type: " + file.getContentType());
        }

        // Remove any prior receipt for this expense regardless of its extension.
        deleteForExpense(expenseId);

        String filename = expenseId + "." + ext;
        Path target = resolve(filename);
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store receipt", e);
        }
        return filename;
    }

    public Resource loadAsResource(String filename) {
        Path file = resolve(filename);
        if (!Files.exists(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Receipt file missing");
        }
        return new FileSystemResource(file);
    }

    public void delete(String filename) {
        if (filename == null) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(filename));
        } catch (IOException ignored) {
            // Best effort — a missing file is fine.
        }
    }

    /** Content type to return when serving {@code filename}, inferred from its extension. */
    public String contentTypeFor(String filename) {
        int dot = filename.lastIndexOf('.');
        String ext = dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "heic" -> "image/heic";
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }

    private void deleteForExpense(UUID expenseId) {
        for (String ext : ALLOWED_TYPES.values()) {
            delete(expenseId + "." + ext);
        }
    }

    /** Resolves a filename against the root, guarding against path traversal. */
    private Path resolve(String filename) {
        Path resolved = root.resolve(filename).normalize();
        if (!resolved.startsWith(root)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid receipt path");
        }
        return resolved;
    }
}
