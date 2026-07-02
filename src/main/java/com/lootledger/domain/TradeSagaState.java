package com.lootledger.domain;

/** Persisted saga state machine for a two-sided trade. */
public enum TradeSagaState {
    STARTED,
    ESCROWED_A,
    ESCROWED_B,
    CROSSED,
    COMPLETED,
    COMPENSATING,
    COMPENSATED,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == COMPENSATED || this == FAILED;
    }
}
