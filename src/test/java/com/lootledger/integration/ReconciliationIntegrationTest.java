package com.lootledger.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.lootledger.economy.EconomyService;
import com.lootledger.recon.ReconciliationService;
import com.lootledger.repository.AccountRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ReconciliationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    EconomyService economy;

    @Autowired
    AccountRepository accounts;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ReconciliationService reconciliation;

    @Test
    void corruptedBalanceCacheIsDetectedThenClears() {
        long player = 40001;
        economy.mint(UUID.randomUUID(), player, "GOLD", 500);
        long accountId = accounts.findByOwnerIdAndAsset(player, "GOLD").orElseThrow().getId();

        assertThat(reconciliation.reconcile().ok()).isTrue();

        // Intentionally corrupt the cached balance, bypassing the ledger.
        jdbc.update("UPDATE account SET balance = balance + 777 WHERE id = ?", accountId);

        var drifted = reconciliation.reconcile();
        assertThat(drifted.ok()).isFalse();
        assertThat(drifted.violations()).anyMatch(v -> v.invariant().equals("CACHE_MATCHES_LEDGER"));

        // Restore so the shared database is left consistent for other tests.
        jdbc.update("UPDATE account SET balance = balance - 777 WHERE id = ?", accountId);
        assertThat(reconciliation.reconcile().ok()).isTrue();
    }
}
