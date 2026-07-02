package com.lootledger.trade;

import com.lootledger.domain.TradeSaga;
import com.lootledger.domain.TradeSagaState;
import com.lootledger.repository.TradeSagaRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically resumes sagas that were interrupted (e.g. by a crash) and are stuck in a non-terminal
 * state, driving each to completion or full compensation.
 */
@Component
public class TradeRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(TradeRecoveryJob.class);
    private static final List<TradeSagaState> NON_TERMINAL = List.of(
            TradeSagaState.STARTED,
            TradeSagaState.ESCROWED_A,
            TradeSagaState.ESCROWED_B,
            TradeSagaState.CROSSED,
            TradeSagaState.COMPENSATING);

    private final TradeSagaRepository sagas;
    private final TradeSagaOrchestrator orchestrator;

    public TradeRecoveryJob(TradeSagaRepository sagas, TradeSagaOrchestrator orchestrator) {
        this.sagas = sagas;
        this.orchestrator = orchestrator;
    }

    @Scheduled(fixedDelayString = "${lootledger.trade.recovery-interval-ms:10000}")
    public int recoverStuckSagas() {
        List<TradeSaga> stuck = sagas.findByStateIn(NON_TERMINAL);
        int recovered = 0;
        for (TradeSaga saga : stuck) {
            try {
                orchestrator.drive(saga.getId());
                recovered++;
            } catch (Exception e) {
                log.warn("Failed to recover saga {}: {}", saga.getExternalId(), e.getMessage());
            }
        }
        if (recovered > 0) {
            log.info("Recovered {} interrupted trade saga(s)", recovered);
        }
        return recovered;
    }
}
