package com.nanaskitchens.api.chat.stt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * OpenAI Whisper transcription (STT_PROVIDER=whisper + OPENAI_API_KEY). Whisper's simple
 * JSON response carries no confidence, so we report 1.0 — the mock provider is the knob
 * for exercising the low-confidence path in tests.
 */
@Component
@ConditionalOnProperty(name = "app.stt.provider", havingValue = "whisper")
public class WhisperSttProvider implements SttProvider {

    private static final Map<String, String> EXTENSIONS = Map.of(
            "audio/webm", "webm", "audio/mp4", "m4a", "audio/mpeg", "mp3", "audio/wav", "wav");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper jsonMapper;
    private final String apiKey;
    private final String baseUrl;

    public WhisperSttProvider(
            JsonMapper jsonMapper,
            @Value("${app.stt.openai-api-key:}") String apiKey,
            @Value("${app.stt.openai-base-url:https://api.openai.com}") String baseUrl) {
        this.jsonMapper = jsonMapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    @Override
    public Transcription transcribe(byte[] audio, String contentType) {
        String boundary = "stt-" + UUID.randomUUID();
        byte[] body = multipart(boundary, audio, contentType);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/audio/transcriptions"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "STT_FAILED");
            }
            JsonNode json = jsonMapper.readTree(response.body());
            return new Transcription(json.get("text").asString().trim(), 1.0);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "STT_FAILED");
        }
    }

    private static byte[] multipart(String boundary, byte[] audio, String contentType) {
        String ext = EXTENSIONS.getOrDefault(contentType == null ? "" : contentType.split(";")[0], "webm");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            String head = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"model\"\r\n\r\nwhisper-1\r\n"
                    + "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"voice." + ext + "\"\r\n"
                    + "Content-Type: " + (contentType == null ? "application/octet-stream" : contentType)
                    + "\r\n\r\n";
            out.write(head.getBytes(StandardCharsets.UTF_8));
            out.write(audio);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
