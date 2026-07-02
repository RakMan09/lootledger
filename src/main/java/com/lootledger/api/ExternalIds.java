package com.lootledger.api;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Derives deterministic ledger external ids from client-supplied idempotency keys. */
public final class ExternalIds {

    private ExternalIds() {
    }

    public static UUID fromKey(String idempotencyKey) {
        return UUID.nameUUIDFromBytes(idempotencyKey.getBytes(StandardCharsets.UTF_8));
    }
}
