package com.nanaskitchens.api.chat.stt;

/**
 * Story 5.3 (FR13) — speech-to-text behind a provider interface, same pattern as
 * payments/delivery/storage: Whisper plugs in when an OPENAI_API_KEY exists; until then
 * the mock keeps the voice pipeline runnable with zero configuration.
 */
public interface SttProvider {

    /** transcript + confidence 0..1 — low confidence makes the agent clarify (AC4). */
    record Transcription(String text, double confidence) {
    }

    Transcription transcribe(byte[] audio, String contentType);
}
