package com.lootledger.loot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lootledger.api.ExternalIds;
import com.lootledger.economy.EconomyService;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes loot events and applies each as a mint from the faucet to the player. Exactly-once
 * ("effectively-once") is achieved by deriving the ledger external id from the loot id: redelivery of
 * the same event is a ledger no-op. The offset is acknowledged only after the DB transaction commits.
 */
@Component
public class LootConsumer {

    private static final Logger log = LoggerFactory.getLogger(LootConsumer.class);

    private final EconomyService economy;
    private final ObjectMapper objectMapper;

    public LootConsumer(EconomyService economy, ObjectMapper objectMapper) {
        this.economy = economy;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${lootledger.topics.loot}", groupId = "lootledger-loot-consumer")
    public void onLoot(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            LootEvent event = objectMapper.readValue(record.value(), LootEvent.class);
            String lootId = event.lootId() != null ? event.lootId() : record.key();
            UUID externalId = ExternalIds.fromKey("loot:" + lootId);
            economy.mint(externalId, event.playerId(), event.asset(), event.amount());
            ack.acknowledge();
            log.debug("Applied loot {} -> player {} +{} {}", lootId, event.playerId(), event.amount(), event.asset());
        } catch (Exception e) {
            // Do not ack: the event will be redelivered. Because application is idempotent, replays
            // are harmless once the underlying issue clears.
            log.error("Failed to apply loot event: {}", e.getMessage(), e);
        }
    }
}
