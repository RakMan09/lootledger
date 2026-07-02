package com.lootledger.loot;

/**
 * A loot drop published to Kafka. {@code lootId} is the natural idempotency key: applying the same
 * loot id twice must be a no-op.
 */
public record LootEvent(String lootId, long playerId, String asset, long amount) {
}
