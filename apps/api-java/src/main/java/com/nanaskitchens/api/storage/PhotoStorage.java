package com.nanaskitchens.api.storage;

/**
 * Story 1.3 AC2 — photo storage behind a provider interface, same pattern as
 * payments/delivery: S3 (+ signed URLs) plugs in when credentials exist; until then the
 * local-disk impl keeps uploads working with zero configuration.
 */
public interface PhotoStorage {

    /** Persists the bytes and returns a publicly resolvable URL. */
    String store(byte[] bytes, String contentType);
}
