package com.lootledger.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "lootledger.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaTopicsConfig {

    @Bean
    public NewTopic lootTopic(@Value("${lootledger.topics.loot}") String loot) {
        return TopicBuilder.name(loot).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic economyTopic(@Value("${lootledger.topics.economy}") String economy) {
        return TopicBuilder.name(economy).partitions(3).replicas(1).build();
    }
}
