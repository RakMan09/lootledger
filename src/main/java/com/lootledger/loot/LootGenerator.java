package com.lootledger.loot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lootledger.economy.SystemAccounts;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Optional demo loot faucet: periodically publishes random loot drops to Kafka. Disabled by default;
 * enable with {@code lootledger.loot-generator.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "lootledger.loot-generator.enabled", havingValue = "true")
public class LootGenerator {

    private static final Logger log = LoggerFactory.getLogger(LootGenerator.class);
    private static final String[] ITEMS = {"legendary_sword", "health_potion", "dragon_scale", "gold_ring"};

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;
    private final String lootTopic;

    public LootGenerator(
            KafkaTemplate<String, String> kafka,
            ObjectMapper objectMapper,
            @Value("${lootledger.topics.loot}") String lootTopic) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.lootTopic = lootTopic;
    }

    @Scheduled(fixedDelayString = "${lootledger.loot-generator.interval-ms:2000}")
    public void drop() {
        try {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            long playerId = rnd.nextLong(1, 11);
            boolean gold = rnd.nextBoolean();
            String asset = gold ? SystemAccounts.GOLD : SystemAccounts.itemAsset(ITEMS[rnd.nextInt(ITEMS.length)]);
            long amount = gold ? rnd.nextLong(10, 500) : 1;
            LootEvent event = new LootEvent(UUID.randomUUID().toString(), playerId, asset, amount);
            kafka.send(lootTopic, event.lootId(), objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.warn("Failed to publish loot drop: {}", e.getMessage());
        }
    }
}
