package com.lootledger.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lootledger.loot.LootEvent;
import com.lootledger.recon.ReconciliationService;
import com.lootledger.repository.AccountRepository;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;

class LootConsumerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AccountRepository accounts;

    @Autowired
    ReconciliationService reconciliation;

    @Value("${lootledger.topics.loot}")
    String lootTopic;

    private long gold(long owner) {
        return accounts.findByOwnerIdAndAsset(owner, "GOLD").map(a -> a.getBalance()).orElse(0L);
    }

    @Test
    void redeliveredLootIsAppliedExactlyOnce() throws Exception {
        long player = 80001;
        String lootId = "loot-once-1";
        LootEvent event = new LootEvent(lootId, player, "GOLD", 250);
        String json = objectMapper.writeValueAsString(event);

        // Publish the SAME loot event twice (simulates at-least-once / offset replay).
        kafka.send(lootTopic, lootId, json).get();
        kafka.send(lootTopic, lootId, json).get();

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(gold(player)).isEqualTo(250));

        // Give the consumer time to (not) apply the duplicate, then confirm still exactly once.
        Thread.sleep(2000);
        assertThat(gold(player)).isEqualTo(250);
        assertThat(reconciliation.reconcile().ok()).isTrue();
    }
}
