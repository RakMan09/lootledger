package com.lootledger.trade;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lootledger.api.dto.TradeRequest;
import com.lootledger.domain.TradeSaga;
import com.lootledger.domain.TradeSagaState;
import com.lootledger.ledger.LedgerException;
import com.lootledger.repository.TradeSagaRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Drives the two-sided trade saga forward through its persisted state machine, compensating if a side
 * cannot be escrowed. Because each step commits independently and is idempotent, a crash at any point
 * can be resumed by re-driving from the last persisted state — the trade always ends COMPLETED or
 * fully COMPENSATED, never leaving value stranded or duplicated.
 */
@Service
public class TradeSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(TradeSagaOrchestrator.class);
    private static final int MAX_TRANSITIONS = 16;

    private final TradeSagaRepository sagas;
    private final TradeSagaSteps steps;
    private final SagaFaultInjector faults;
    private final ObjectMapper objectMapper;

    public TradeSagaOrchestrator(
            TradeSagaRepository sagas,
            TradeSagaSteps steps,
            SagaFaultInjector faults,
            ObjectMapper objectMapper) {
        this.sagas = sagas;
        this.steps = steps;
        this.faults = faults;
        this.objectMapper = objectMapper;
    }

    /** Start a trade (or replay/resume an existing one for the same external id) and drive it to a terminal state. */
    public TradeSaga startAndRun(UUID externalId, TradeRequest request) {
        TradeSaga saga = sagas.findByExternalId(externalId).orElseGet(() -> create(externalId, request));
        if (saga.getState().isTerminal()) {
            return saga;
        }
        return drive(saga.getId());
    }

    private TradeSaga create(UUID externalId, TradeRequest request) {
        try {
            OffsetDateTime now = OffsetDateTime.now();
            return sagas.saveAndFlush(TradeSaga.builder()
                    .externalId(externalId)
                    .state(TradeSagaState.STARTED)
                    .payload(serialize(request))
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        } catch (DataIntegrityViolationException race) {
            return sagas.findByExternalId(externalId).orElseThrow(() -> race);
        }
    }

    /** Advance the saga from its current persisted state until it reaches a terminal state. */
    public TradeSaga drive(long sagaId) {
        for (int i = 0; i < MAX_TRANSITIONS; i++) {
            TradeSaga saga = sagas.findById(sagaId)
                    .orElseThrow(() -> new IllegalStateException("Saga not found: " + sagaId));
            TradeSagaState state = saga.getState();
            if (state.isTerminal()) {
                return saga;
            }
            switch (state) {
                case STARTED -> {
                    faults.maybeCrash("before-escrow-a");
                    try {
                        steps.escrowA(sagaId);
                    } catch (LedgerException e) {
                        log.info("Trade {} cannot escrow side A: {}", saga.getExternalId(), e.getMessage());
                        steps.markState(sagaId, TradeSagaState.FAILED, e.getMessage());
                    }
                    faults.maybeCrash("after-escrow-a");
                }
                case ESCROWED_A -> {
                    try {
                        steps.escrowB(sagaId);
                    } catch (LedgerException e) {
                        log.info("Trade {} cannot escrow side B, compensating: {}", saga.getExternalId(), e.getMessage());
                        steps.markState(sagaId, TradeSagaState.COMPENSATING, e.getMessage());
                    }
                    faults.maybeCrash("after-escrow-b");
                }
                case ESCROWED_B -> {
                    steps.cross(sagaId);
                    faults.maybeCrash("after-cross");
                }
                case CROSSED -> steps.complete(sagaId);
                case COMPENSATING -> steps.compensate(sagaId);
                default -> {
                    return saga;
                }
            }
        }
        throw new IllegalStateException("Saga " + sagaId + " did not converge to a terminal state");
    }

    private String serialize(TradeRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize trade request", e);
        }
    }
}
