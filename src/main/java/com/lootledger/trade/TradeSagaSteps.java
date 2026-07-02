package com.lootledger.trade;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lootledger.api.dto.AssetAmount;
import com.lootledger.api.dto.TradeRequest;
import com.lootledger.domain.Account;
import com.lootledger.domain.AccountKind;
import com.lootledger.domain.TradeSaga;
import com.lootledger.domain.TradeSagaState;
import com.lootledger.economy.SystemAccounts;
import com.lootledger.ledger.LedgerService;
import com.lootledger.ledger.PostingLine;
import com.lootledger.outbox.OutboxService;
import com.lootledger.repository.TradeSagaRepository;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Individual, independently-committing saga steps. Each step posts an idempotent ledger transfer
 * (deterministic external id) and advances the persisted saga state in the same transaction, so a
 * crash between steps leaves durable, resumable progress and never strands or duplicates value.
 */
@Service
public class TradeSagaSteps {

    private final LedgerService ledger;
    private final TradeSagaRepository sagas;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public TradeSagaSteps(
            LedgerService ledger,
            TradeSagaRepository sagas,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.ledger = ledger;
        this.sagas = sagas;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    private TradeSaga getRequired(long sagaId) {
        return sagas.findById(sagaId)
                .orElseThrow(() -> new IllegalStateException("Saga not found: " + sagaId));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void escrowA(long sagaId) {
        TradeSaga saga = getRequired(sagaId);
        TradeRequest req = parse(saga.getPayload());
        List<PostingLine> lines = escrowLines(req.partyAOwnerId(), req.aGives());
        ledger.post(stepId(saga.getExternalId(), "escrow-a"), "ESCROW", lines);
        transition(saga, TradeSagaState.ESCROWED_A);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void escrowB(long sagaId) {
        TradeSaga saga = getRequired(sagaId);
        TradeRequest req = parse(saga.getPayload());
        List<PostingLine> lines = escrowLines(req.partyBOwnerId(), req.bGives());
        ledger.post(stepId(saga.getExternalId(), "escrow-b"), "ESCROW", lines);
        transition(saga, TradeSagaState.ESCROWED_B);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void cross(long sagaId) {
        TradeSaga saga = getRequired(sagaId);
        TradeRequest req = parse(saga.getPayload());
        List<PostingLine> lines = new ArrayList<>();
        // A's escrowed assets go to B.
        lines.addAll(crossLines(req.partyBOwnerId(), req.aGives()));
        // B's escrowed assets go to A.
        lines.addAll(crossLines(req.partyAOwnerId(), req.bGives()));
        ledger.post(stepId(saga.getExternalId(), "cross"), "TRADE", lines);
        transition(saga, TradeSagaState.CROSSED);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void complete(long sagaId) {
        TradeSaga saga = getRequired(sagaId);
        transition(saga, TradeSagaState.COMPLETED);
        outbox.enqueue("trade", "TradeCompleted", Map.of(
                "externalId", saga.getExternalId().toString(),
                "state", TradeSagaState.COMPLETED.name()));
    }

    /** Release party A's escrow back (only A was escrowed when we compensate). */
    @Transactional(propagation = Propagation.REQUIRED)
    public void compensate(long sagaId) {
        TradeSaga saga = getRequired(sagaId);
        TradeRequest req = parse(saga.getPayload());
        List<PostingLine> lines = releaseLines(req.partyAOwnerId(), req.aGives());
        ledger.post(stepId(saga.getExternalId(), "compensate"), "COMPENSATE", lines);
        transition(saga, TradeSagaState.COMPENSATED);
        outbox.enqueue("trade", "TradeCompensated", Map.of(
                "externalId", saga.getExternalId().toString(),
                "state", TradeSagaState.COMPENSATED.name()));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void markState(long sagaId, TradeSagaState state, String error) {
        TradeSaga saga = getRequired(sagaId);
        saga.setLastError(error);
        transition(saga, state);
    }

    private void transition(TradeSaga saga, TradeSagaState state) {
        saga.setState(state);
        saga.setUpdatedAt(OffsetDateTime.now());
        sagas.save(saga);
    }

    private List<PostingLine> escrowLines(long ownerId, List<AssetAmount> gives) {
        List<PostingLine> lines = new ArrayList<>();
        for (AssetAmount give : gives) {
            Account player = ledger.getOrCreateAccount(ownerId, give.asset(), AccountKind.PLAYER);
            Account escrow = ledger.getOrCreateAccount(SystemAccounts.ESCROW_OWNER, give.asset(), AccountKind.ESCROW);
            lines.add(new PostingLine(player.getId(), give.asset(), -give.amount()));
            lines.add(new PostingLine(escrow.getId(), give.asset(), give.amount()));
        }
        return lines;
    }

    private List<PostingLine> crossLines(long recipientOwnerId, List<AssetAmount> assets) {
        List<PostingLine> lines = new ArrayList<>();
        for (AssetAmount asset : assets) {
            Account escrow = ledger.getOrCreateAccount(SystemAccounts.ESCROW_OWNER, asset.asset(), AccountKind.ESCROW);
            Account recipient = ledger.getOrCreateAccount(recipientOwnerId, asset.asset(), AccountKind.PLAYER);
            lines.add(new PostingLine(escrow.getId(), asset.asset(), -asset.amount()));
            lines.add(new PostingLine(recipient.getId(), asset.asset(), asset.amount()));
        }
        return lines;
    }

    private List<PostingLine> releaseLines(long ownerId, List<AssetAmount> gives) {
        List<PostingLine> lines = new ArrayList<>();
        for (AssetAmount give : gives) {
            Account escrow = ledger.getOrCreateAccount(SystemAccounts.ESCROW_OWNER, give.asset(), AccountKind.ESCROW);
            Account player = ledger.getOrCreateAccount(ownerId, give.asset(), AccountKind.PLAYER);
            lines.add(new PostingLine(escrow.getId(), give.asset(), -give.amount()));
            lines.add(new PostingLine(player.getId(), give.asset(), give.amount()));
        }
        return lines;
    }

    private TradeRequest parse(String payload) {
        try {
            return objectMapper.readValue(payload, TradeRequest.class);
        } catch (Exception e) {
            throw new IllegalStateException("Corrupt saga payload", e);
        }
    }

    public static UUID stepId(UUID base, String step) {
        return UUID.nameUUIDFromBytes((base.toString() + ":" + step).getBytes(StandardCharsets.UTF_8));
    }
}
