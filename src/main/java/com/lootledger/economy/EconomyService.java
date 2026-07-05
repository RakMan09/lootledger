package com.lootledger.economy;

import com.lootledger.domain.Account;
import com.lootledger.domain.AccountKind;
import com.lootledger.domain.Transfer;
import com.lootledger.ledger.LedgerService;
import com.lootledger.ledger.PostingLine;
import com.lootledger.metrics.EconomyMetrics;
import com.lootledger.outbox.OutboxService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * High-level economy operations composed from balanced ledger transfers. Every mutation also writes
 * a transactional-outbox event so downstream consumers stay in sync.
 */
@Service
public class EconomyService {

    private final LedgerService ledger;
    private final OutboxService outbox;
    private final EconomyMetrics metrics;

    public EconomyService(LedgerService ledger, OutboxService outbox, EconomyMetrics metrics) {
        this.ledger = ledger;
        this.outbox = outbox;
        this.metrics = metrics;
    }

    /** Move an asset between two players. Debits the sender, credits the receiver. */
    @Transactional(propagation = Propagation.REQUIRED)
    public Transfer transfer(UUID externalId, long fromOwner, long toOwner, String asset, long amount) {
        requirePositive(amount);
        long start = System.nanoTime();
        Account from = ledger.getOrCreateAccount(fromOwner, asset, AccountKind.PLAYER);
        Account to = ledger.getOrCreateAccount(toOwner, asset, AccountKind.PLAYER);
        Transfer transfer = ledger.post(externalId, "TRANSFER", List.of(
                new PostingLine(from.getId(), asset, -amount),
                new PostingLine(to.getId(), asset, amount)));
        outbox.enqueue("transfer", "TransferCompleted", Map.of(
                "externalId", externalId.toString(),
                "type", "TRANSFER",
                "fromOwner", fromOwner,
                "toOwner", toOwner,
                "asset", asset,
                "amount", amount));
        metrics.transferLatencyTimer().record(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS);
        metrics.recordTransfer();
        return transfer;
    }

    /** Mint an asset into a player's account from the faucet (loot / admin credit). */
    @Transactional(propagation = Propagation.REQUIRED)
    public Transfer mint(UUID externalId, long toOwner, String asset, long amount) {
        requirePositive(amount);
        Account faucet = ledger.getOrCreateAccount(SystemAccounts.FAUCET_OWNER, asset, AccountKind.FAUCET);
        Account player = ledger.getOrCreateAccount(toOwner, asset, AccountKind.PLAYER);
        Transfer transfer = ledger.post(externalId, "LOOT", List.of(
                new PostingLine(faucet.getId(), asset, -amount),
                new PostingLine(player.getId(), asset, amount)));
        outbox.enqueue("loot", "LootApplied", Map.of(
                "externalId", externalId.toString(),
                "type", "LOOT",
                "toOwner", toOwner,
                "asset", asset,
                "amount", amount));
        metrics.recordMint();
        return transfer;
    }

    private void requirePositive(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive, got " + amount);
        }
    }
}
