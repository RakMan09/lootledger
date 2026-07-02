package com.lootledger.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lootledger.api.dto.TransferRequest;
import com.lootledger.idempotency.IdempotencyService;
import org.junit.jupiter.api.Test;

class IdempotencyHashTest {

    private final IdempotencyService service = new IdempotencyService(null, null, new ObjectMapper());

    @Test
    void identicalRequestsHashEqual() {
        TransferRequest a = new TransferRequest(1L, 2L, "GOLD", 100);
        TransferRequest b = new TransferRequest(1L, 2L, "GOLD", 100);
        assertThat(service.hashRequest(a)).isEqualTo(service.hashRequest(b));
    }

    @Test
    void differentRequestsHashDifferently() {
        TransferRequest a = new TransferRequest(1L, 2L, "GOLD", 100);
        TransferRequest b = new TransferRequest(1L, 2L, "GOLD", 101);
        assertThat(service.hashRequest(a)).isNotEqualTo(service.hashRequest(b));
    }
}
