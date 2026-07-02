package com.lootledger.domain;

/** Lifecycle status of an idempotency key row. */
public enum IdempotencyStatus {
    IN_FLIGHT,
    SUCCEEDED,
    FAILED
}
