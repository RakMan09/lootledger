package com.lootledger.idempotency;

/** Business logic to run exactly once for a given idempotency key. */
@FunctionalInterface
public interface IdempotentAction {
    ActionResult run();

    /**
     * Result of a first-time execution.
     *
     * @param httpStatus   HTTP status to return and persist
     * @param responseBody object to serialize as the stored/returned response body
     * @param transferId   optional ledger transfer id produced by the action
     */
    record ActionResult(int httpStatus, Object responseBody, Long transferId) {
    }
}
