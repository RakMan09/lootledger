package com.lootledger.unit;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lootledger.api.dto.TransferRequest;
import com.lootledger.idempotency.IdempotencyService;
import com.lootledger.metrics.EconomyMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

class IdempotencyHashTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<StringRedisTemplate> noRedis = mock(ObjectProvider.class);

    private final IdempotencyService service =
            new IdempotencyService(null, noRedis, new ObjectMapper(), false,
                    new EconomyMetrics(new SimpleMeterRegistry()));

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
