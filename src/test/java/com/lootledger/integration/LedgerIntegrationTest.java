package com.lootledger.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lootledger.economy.EconomyService;
import com.lootledger.ledger.LedgerException;
import com.lootledger.recon.ReconciliationService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class LedgerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    EconomyService economy;

    @Autowired
    ReconciliationService reconciliation;

    @Test
    void mintThenTransferMovesGoldAndConserves() {
        long alice = 1001;
        long bob = 1002;
        economy.mint(UUID.randomUUID(), alice, "GOLD", 500);
        economy.transfer(UUID.randomUUID(), alice, bob, "GOLD", 200);

        assertThat(economy.transfer(UUID.randomUUID(), alice, bob, "GOLD", 100)).isNotNull();

        assertThat(reconciliation.reconcile().ok()).isTrue();
    }

    @Test
    void overdraftIsRejected() {
        long carol = 2001;
        long dave = 2002;
        economy.mint(UUID.randomUUID(), carol, "GOLD", 50);

        assertThatThrownBy(() -> economy.transfer(UUID.randomUUID(), carol, dave, "GOLD", 999))
                .isInstanceOf(LedgerException.class);

        assertThat(reconciliation.reconcile().ok()).isTrue();
    }

    @Test
    void duplicateExternalIdIsNoOp() {
        long p = 3001;
        UUID ext = UUID.randomUUID();
        economy.mint(UUID.randomUUID(), p, "GOLD", 100);
        // Same external id applied twice must not double-credit.
        economy.transfer(ext, p, 3002L, "GOLD", 40);
        economy.transfer(ext, p, 3002L, "GOLD", 40);

        assertThat(economy.transfer(UUID.randomUUID(), p, 3002L, "GOLD", 10)).isNotNull();
        assertThat(reconciliation.reconcile().ok()).isTrue();
    }
}
