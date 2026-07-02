package com.lootledger.idempotency;

/**
 * Outcome of an idempotent execution.
 *
 * @param httpStatus HTTP status to return to the client
 * @param jsonBody   serialized JSON response body
 * @param replayed   true if this is a stored replay of a prior identical request
 */
public record IdempotencyResult(int httpStatus, String jsonBody, boolean replayed) {
}
