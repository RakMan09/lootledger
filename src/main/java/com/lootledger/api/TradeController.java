package com.lootledger.api;

import com.lootledger.api.dto.TradeRequest;
import com.lootledger.api.dto.TradeResponse;
import com.lootledger.domain.TradeSaga;
import com.lootledger.domain.TradeSagaState;
import com.lootledger.repository.TradeSagaRepository;
import com.lootledger.trade.TradeSagaOrchestrator;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Two-sided trade endpoint. Idempotency comes from the saga's unique external id (derived from the
 * {@code Idempotency-Key}) plus the deterministic per-step ledger external ids.
 */
@RestController
@RequestMapping("/trades")
public class TradeController {

    private final TradeSagaOrchestrator orchestrator;
    private final TradeSagaRepository sagas;

    public TradeController(TradeSagaOrchestrator orchestrator, TradeSagaRepository sagas) {
        this.orchestrator = orchestrator;
        this.sagas = sagas;
    }

    @PostMapping
    public ResponseEntity<TradeResponse> trade(
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody TradeRequest request) {
        UUID externalId = ExternalIds.fromKey(key);
        TradeSaga saga = orchestrator.startAndRun(externalId, request);
        TradeResponse body = new TradeResponse(saga.getId(), saga.getExternalId().toString(), saga.getState().name());
        HttpStatus status = saga.getState() == TradeSagaState.COMPLETED
                ? HttpStatus.CREATED
                : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(body);
    }

    @GetMapping("/{key}")
    public TradeResponse get(@PathVariable String key) {
        UUID externalId = ExternalIds.fromKey(key);
        TradeSaga saga = sagas.findByExternalId(externalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such trade"));
        return new TradeResponse(saga.getId(), saga.getExternalId().toString(), saga.getState().name());
    }
}
