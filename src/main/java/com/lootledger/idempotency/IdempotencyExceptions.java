package com.lootledger.idempotency;

/** Container for idempotency-related exceptions. */
public final class IdempotencyExceptions {

    private IdempotencyExceptions() {
    }

    /** The same key was reused with a different request body (HTTP 422). */
    public static class KeyReusedException extends RuntimeException {
        public KeyReusedException(String key) {
            super("Idempotency-Key '" + key + "' was already used with a different request body");
        }
    }

    /** A request with this key is still being processed (HTTP 409). */
    public static class InFlightException extends RuntimeException {
        public InFlightException(String key) {
            super("A request with Idempotency-Key '" + key + "' is currently in flight");
        }
    }
}
