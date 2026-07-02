package com.lootledger.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes domain events into the {@code outbox} table within the caller's transaction. Because the
 * outbox row and the ledger mutation commit together, downstream events can never diverge from state.
 */
@Service
public class OutboxService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public OutboxService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String aggregate, String eventType, Object payload) {
        String json = serialize(payload);
        jdbc.update(
                "INSERT INTO outbox (aggregate, event_type, payload) VALUES (?, ?, ?::jsonb)",
                aggregate, eventType, json);
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }
}
