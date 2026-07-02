package com.lootledger.api;

import com.lootledger.api.dto.MintRequest;
import com.lootledger.api.dto.TransferRequest;
import com.lootledger.api.dto.TransferResponse;
import com.lootledger.domain.Transfer;
import com.lootledger.economy.EconomyService;
import com.lootledger.idempotency.IdempotencyResult;
import com.lootledger.idempotency.IdempotencyService;
import com.lootledger.idempotency.IdempotentAction;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Mutating economy endpoints. Every request must carry an {@code Idempotency-Key} header. */
@RestController
public class TransferController {

    private final EconomyService economy;
    private final IdempotencyService idempotency;

    public TransferController(EconomyService economy, IdempotencyService idempotency) {
        this.economy = economy;
        this.idempotency = idempotency;
    }

    @PostMapping(path = "/transfers", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> transfer(
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody TransferRequest request) {
        String hash = idempotency.hashRequest(request);
        IdempotencyResult result = idempotency.execute(key, hash, () -> {
            UUID externalId = ExternalIds.fromKey(key);
            Transfer transfer = economy.transfer(
                    externalId, request.fromOwnerId(), request.toOwnerId(), request.asset(), request.amount());
            TransferResponse body = new TransferResponse(
                    transfer.getId(), externalId.toString(), transfer.getType(), "COMPLETED");
            return new IdempotentAction.ActionResult(201, body, transfer.getId());
        });
        return toResponse(result);
    }

    @PostMapping(path = "/admin/mint", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> mint(
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody MintRequest request) {
        String hash = idempotency.hashRequest(request);
        IdempotencyResult result = idempotency.execute(key, hash, () -> {
            UUID externalId = ExternalIds.fromKey(key);
            Transfer transfer = economy.mint(externalId, request.toOwnerId(), request.asset(), request.amount());
            TransferResponse body = new TransferResponse(
                    transfer.getId(), externalId.toString(), transfer.getType(), "COMPLETED");
            return new IdempotentAction.ActionResult(201, body, transfer.getId());
        });
        return toResponse(result);
    }

    private ResponseEntity<String> toResponse(IdempotencyResult result) {
        return ResponseEntity.status(result.httpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotent-Replayed", Boolean.toString(result.replayed()))
                .body(result.jsonBody());
    }
}
