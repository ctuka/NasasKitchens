package com.nanaskitchens.api.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Serves LocalPhotoStorage uploads (dev stand-in for S3 signed URLs, Story 1.3 AC2). */
@RestController
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
public class FilesController {

    private final LocalPhotoStorage storage;

    public FilesController(LocalPhotoStorage storage) {
        this.storage = storage;
    }

    @GetMapping("/files/{name}")
    public ResponseEntity<byte[]> serve(@PathVariable String name) throws IOException {
        // UUID.ext names only — rejects traversal and anything we didn't write ourselves.
        if (!name.matches("[0-9a-f-]{36}\\.(jpg|png|webp|pdf)")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Path file = storage.resolve(name);
        if (!Files.exists(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        MediaType type = switch (name.substring(name.lastIndexOf('.') + 1)) {
            case "png" -> MediaType.IMAGE_PNG;
            case "webp" -> MediaType.parseMediaType("image/webp");
            case "pdf" -> MediaType.APPLICATION_PDF;
            default -> MediaType.IMAGE_JPEG;
        };
        return ResponseEntity.ok()
                .contentType(type)
                .header("Cache-Control", "public, max-age=86400, immutable")
                .body(Files.readAllBytes(file));
    }
}
