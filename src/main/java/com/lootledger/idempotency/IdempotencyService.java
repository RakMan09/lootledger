package com.lootledger.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lootledger.domain.IdempotencyStatus;
import com.lootledger.metrics.EconomyMetrics;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarantees that a mutating request is applied at most once. The authority is the Postgres
 * {@code idempotency_key} table: the {@code UNIQUE(key)} constraint is the serialization point.
 * Redis is only a best-effort fast path for replaying already-known responses.
 *
 * <p>The claim + business logic + status flip all commit in a single transaction, so partial state
 * can never leak: either the transfer and the SUCCEEDED key both persist, or neither does. Two
 * concurrent first-time requests with the same key serialize on the unique index — the loser blocks
 * until the winner commits, then replays the stored response.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final Duration REDIS_TTL = Duration.ofHours(24);
    private static final String REDIS_PREFIX = "idem:resp:";

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final boolean redisEnabled;
    private final EconomyMetrics metrics;

    public IdempotencyService(
            JdbcTemplate jdbc,
            ObjectProvider<StringRedisTemplate> redisProvider,
            ObjectMapper objectMapper,
            @Value("${lootledger.redis.enabled:true}") boolean redisEnabled,
            EconomyMetrics metrics) {
        this.jdbc = jdbc;
        this.redis = redisProvider.getIfAvailable();
        this.objectMapper = objectMapper;
        this.redisEnabled = redisEnabled && this.redis != null;
        this.metrics = metrics;
    }

    /** Hash the canonical request body so key reuse with a different body can be detected. */
    public String hashRequest(Object request) {
        try {
            byte[] canonical = objectMapper.writeValueAsBytes(request);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(canonical));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash request", e);
        }
    }

    /**
     * Execute {@code action} at most once for {@code key}. Duplicate calls with a matching request
     * hash replay the stored response; a mismatched hash raises
     * {@link IdempotencyExceptions.KeyReusedException}.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public IdempotencyResult execute(String key, String requestHash, IdempotentAction action) {
        // Fast path: a previously completed response cached in Redis.
        StoredResponse fromCache = readCache(key);
        if (fromCache != null && fromCache.requestHash().equals(requestHash)) {
            log.debug("Idempotency replay from Redis for key {}", key);
            metrics.recordIdempotentReplay();
            return new IdempotencyResult(fromCache.code(), fromCache.body(), true);
        }

        int inserted = jdbc.update(
                "INSERT INTO idempotency_key (key, request_hash, status) VALUES (?, ?, 'IN_FLIGHT') "
                        + "ON CONFLICT (key) DO NOTHING",
                key, requestHash);

        if (inserted == 1) {
            // We own this request. Run the business logic in this same transaction.
            IdempotentAction.ActionResult result = action.run();
            String body = serialize(result.responseBody());
            jdbc.update(
                    "UPDATE idempotency_key SET status = ?, response_code = ?, "
                            + "response_body = ?::jsonb, transfer_id = ? WHERE key = ?",
                    IdempotencyStatus.SUCCEEDED.name(), result.httpStatus(), body, result.transferId(), key);
            cacheResponse(key, requestHash, result.httpStatus(), body);
            metrics.recordIdempotentExecution();
            log.info("Idempotency key {} executed and committed (status {})", key, result.httpStatus());
            return new IdempotencyResult(result.httpStatus(), body, false);
        }

        // A row already exists (committed by another transaction). Read and honor it.
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap(
                    "SELECT request_hash, status, response_code, response_body::text AS response_body "
                            + "FROM idempotency_key WHERE key = ?",
                    key);
        } catch (EmptyResultDataAccessException e) {
            // Extremely rare race: owner rolled back between our insert-conflict and this read.
            throw new IdempotencyExceptions.InFlightException(key);
        }

        String storedHash = (String) row.get("request_hash");
        if (!requestHash.equals(storedHash)) {
            throw new IdempotencyExceptions.KeyReusedException(key);
        }

        String status = (String) row.get("status");
        if (IdempotencyStatus.SUCCEEDED.name().equals(status)
                || IdempotencyStatus.FAILED.name().equals(status)) {
            Integer code = (Integer) row.get("response_code");
            String body = (String) row.get("response_body");
            cacheResponse(key, requestHash, code, body);
            metrics.recordIdempotentReplay();
            log.debug("Idempotency replay from Postgres for key {} (status {})", key, status);
            return new IdempotencyResult(code, body, true);
        }

        // IN_FLIGHT committed elsewhere (only possible via an external reaper path).
        throw new IdempotencyExceptions.InFlightException(key);
    }

    private void cacheResponse(String key, String requestHash, int code, String body) {
        if (!redisEnabled) {
            return;
        }
        try {
            StoredResponse stored = new StoredResponse(requestHash, code, body);
            redis.opsForValue().set(REDIS_PREFIX + key, objectMapper.writeValueAsString(stored), REDIS_TTL);
        } catch (Exception e) {
            log.warn("Failed to cache idempotency response in Redis for key {}: {}", key, e.getMessage());
        }
    }

    private StoredResponse readCache(String key) {
        if (!redisEnabled) {
            return null;
        }
        try {
            String json = redis.opsForValue().get(REDIS_PREFIX + key);
            return json == null ? null : objectMapper.readValue(json, StoredResponse.class);
        } catch (Exception e) {
            log.warn("Redis unavailable for idempotency fast-path: {}", e.getMessage());
            return null;
        }
    }

    private String serialize(Object body) {
        if (body == null) {
            return "null";
        }
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize response body", e);
        }
    }

    public record StoredResponse(String requestHash, int code, String body) {
    }
}
