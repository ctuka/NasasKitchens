package com.nanaskitchens.api.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Local-disk photo storage for dev/demo; files are served back by FilesController. */
@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalPhotoStorage implements PhotoStorage {

    /** All storable types; endpoints narrow this (photos: images only, reports: + pdf). */
    public static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "application/pdf", "pdf");

    private final Path dir;
    private final String publicBaseUrl;

    public LocalPhotoStorage(
            @Value("${app.storage.local-dir:uploads}") String localDir,
            @Value("${app.storage.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.dir = Path.of(localDir);
        this.publicBaseUrl = publicBaseUrl;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String store(byte[] bytes, String contentType) {
        String name = UUID.randomUUID() + "." + EXTENSIONS.get(contentType);
        try {
            Files.write(dir.resolve(name), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return publicBaseUrl + "/files/" + name;
    }

    Path resolve(String name) {
        return dir.resolve(name);
    }
}
