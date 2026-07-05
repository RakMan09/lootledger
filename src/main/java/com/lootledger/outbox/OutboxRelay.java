package com.lootledger.outbox;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls the outbox for unpublished rows and publishes them to Kafka, then marks them published — all
 * within one transaction using {@code FOR UPDATE SKIP LOCKED} so multiple relay instances can run
 * concurrently without double-publishing a row.
 */
@Component
@ConditionalOnProperty(name = "lootledger.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final String economyTopic;
    private final int batchSize;

    public OutboxRelay(
            JdbcTemplate jdbc,
            KafkaTemplate<String, String> kafka,
            @Value("${lootledger.topics.economy}") String economyTopic,
            @Value("${lootledger.outbox.batch-size:200}") int batchSize) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.economyTopic = economyTopic;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${lootledger.outbox.relay-interval-ms:1000}")
    public int relay() {
        try {
            return publishBatch();
        } catch (Exception e) {
            log.warn("Outbox relay iteration failed: {}", e.getMessage());
            return 0;
        }
    }

    @Transactional
    public int publishBatch() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, aggregate, event_type, payload::text AS payload FROM outbox "
                        + "WHERE published = FALSE ORDER BY id ASC LIMIT ? FOR UPDATE SKIP LOCKED",
                batchSize);
        if (rows.isEmpty()) {
            return 0;
        }
        for (Map<String, Object> row : rows) {
            long id = ((Number) row.get("id")).longValue();
            String eventType = (String) row.get("event_type");
            String payload = (String) row.get("payload");
            // Synchronous send: block until the broker acks so we only mark published on success.
            kafka.send(economyTopic, eventType, payload).join();
            jdbc.update("UPDATE outbox SET published = TRUE WHERE id = ?", id);
        }
        log.info("Outbox relay published {} event(s)", rows.size());
        return rows.size();
    }
}
