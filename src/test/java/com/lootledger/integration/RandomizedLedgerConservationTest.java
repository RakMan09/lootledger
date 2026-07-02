package com.lootledger.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.lootledger.domain.AccountKind;
import com.lootledger.economy.EconomyService;
import com.lootledger.ledger.LedgerException;
import com.lootledger.recon.ReconciliationService;
import com.lootledger.repository.AccountRepository;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Fires a long random stream of mints and transfers against the real ledger and asserts, after
 * replay, that gold is conserved and no player balance is negative — regardless of the sequence.
 */
class RandomizedLedgerConservationTest extends AbstractIntegrationTest {

    @Autowired
    EconomyService economy;

    @Autowired
    AccountRepository accounts;

    @Autowired
    ReconciliationService reconciliation;

    @Test
    void randomMintsAndTransfersConserveValue() {
        long base = 700000; // isolate this test's players
        int players = 8;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        for (int i = 0; i < 400; i++) {
            long a = base + rnd.nextInt(players);
            long b = base + rnd.nextInt(players);
            long amount = rnd.nextLong(1, 500);
            try {
                if (rnd.nextInt(3) == 0) {
                    economy.mint(UUID.randomUUID(), a, "GOLD", amount);
                } else if (a != b) {
                    economy.transfer(UUID.randomUUID(), a, b, "GOLD", amount);
                }
            } catch (LedgerException overdraft) {
                // Expected when a player lacks funds; the transfer is rejected atomically.
            }
        }

        for (int i = 0; i < players; i++) {
            long bal = accounts.findByOwnerIdAndAsset(base + i, "GOLD").map(x -> x.getBalance()).orElse(0L);
            assertThat(bal).as("player %d balance non-negative", base + i).isGreaterThanOrEqualTo(0);
        }
        long faucet = accounts.findByOwnerIdAndAsset(0L, "GOLD")
                .filter(x -> x.getKind() == AccountKind.FAUCET)
                .map(x -> x.getBalance())
                .orElse(0L);
        assertThat(faucet).isLessThanOrEqualTo(0);

        assertThat(reconciliation.reconcile().ok()).isTrue();
    }
}
