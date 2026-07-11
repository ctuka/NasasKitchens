package com.nanaskitchens.api.chat.stt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Dev stand-in until Whisper credentials exist: any upload "transcribes" to a canned
 * ordering utterance so the full voice → agent path stays demoable. Override the text
 * and confidence via env to exercise the low-confidence clarify path (Story 5.2 AC4).
 */
@Component
@ConditionalOnProperty(name = "app.stt.provider", havingValue = "mock", matchIfMissing = true)
public class MockSttProvider implements SttProvider {

    private final String text;
    private final double confidence;

    public MockSttProvider(
            @Value("${app.stt.mock-text:What Turkish kitchens are near me today?}") String text,
            @Value("${app.stt.mock-confidence:0.95}") double confidence) {
        this.text = text;
        this.confidence = confidence;
    }

    @Override
    public Transcription transcribe(byte[] audio, String contentType) {
        return new Transcription(text, confidence);
    }
}
