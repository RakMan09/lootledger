package com.lootledger.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.lootledger.api.ExternalIds;
import com.lootledger.trade.TradeSagaSteps;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExternalIdsTest {

    @Test
    void sameKeyProducesSameExternalId() {
        assertThat(ExternalIds.fromKey("abc")).isEqualTo(ExternalIds.fromKey("abc"));
    }

    @Test
    void differentKeysProduceDifferentExternalIds() {
        assertThat(ExternalIds.fromKey("abc")).isNotEqualTo(ExternalIds.fromKey("abd"));
    }

    @Test
    void stepIdsAreDeterministicAndDistinctPerStep() {
        UUID base = ExternalIds.fromKey("trade-1");
        assertThat(TradeSagaSteps.stepId(base, "escrow-a"))
                .isEqualTo(TradeSagaSteps.stepId(base, "escrow-a"));
        assertThat(TradeSagaSteps.stepId(base, "escrow-a"))
                .isNotEqualTo(TradeSagaSteps.stepId(base, "escrow-b"));
    }
}
