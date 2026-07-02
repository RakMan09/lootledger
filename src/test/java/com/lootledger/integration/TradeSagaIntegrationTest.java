package com.lootledger.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lootledger.api.dto.AssetAmount;
import com.lootledger.api.dto.TradeRequest;
import com.lootledger.domain.TradeSaga;
import com.lootledger.domain.TradeSagaState;
import com.lootledger.economy.EconomyService;
import com.lootledger.recon.ReconciliationService;
import com.lootledger.repository.AccountRepository;
import com.lootledger.trade.SagaFaultInjector;
import com.lootledger.trade.TradeRecoveryJob;
import com.lootledger.trade.TradeSagaOrchestrator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TradeSagaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    TradeSagaOrchestrator orchestrator;

    @Autowired
    EconomyService economy;

    @Autowired
    AccountRepository accounts;

    @Autowired
    SagaFaultInjector faults;

    @Autowired
    TradeRecoveryJob recovery;

    @Autowired
    ReconciliationService reconciliation;

    @AfterEach
    void disarm() {
        faults.disarm();
    }

    private long balance(long owner, String asset) {
        return accounts.findByOwnerIdAndAsset(owner, asset).map(a -> a.getBalance()).orElse(0L);
    }

    @Test
    void happyPathSwapsAssetsAndConserves() {
        long a = 5001;
        long b = 5002;
        economy.mint(UUID.randomUUID(), a, "GOLD", 300);
        economy.mint(UUID.randomUUID(), b, "ITEM:legendary_sword", 1);

        TradeRequest req = new TradeRequest(a, b,
                List.of(new AssetAmount("GOLD", 300)),
                List.of(new AssetAmount("ITEM:legendary_sword", 1)));
        TradeSaga saga = orchestrator.startAndRun(UUID.randomUUID(), req);

        assertThat(saga.getState()).isEqualTo(TradeSagaState.COMPLETED);
        assertThat(balance(a, "GOLD")).isZero();
        assertThat(balance(b, "GOLD")).isEqualTo(300);
        assertThat(balance(a, "ITEM:legendary_sword")).isEqualTo(1);
        assertThat(balance(b, "ITEM:legendary_sword")).isZero();
        assertThat(reconciliation.reconcile().ok()).isTrue();
    }

    @Test
    void insufficientCounterpartyCompensatesAndRestores() {
        long a = 6001;
        long b = 6002; // b has nothing
        economy.mint(UUID.randomUUID(), a, "GOLD", 200);

        TradeRequest req = new TradeRequest(a, b,
                List.of(new AssetAmount("GOLD", 200)),
                List.of(new AssetAmount("ITEM:dragon_scale", 1)));
        TradeSaga saga = orchestrator.startAndRun(UUID.randomUUID(), req);

        assertThat(saga.getState()).isEqualTo(TradeSagaState.COMPENSATED);
        // A's gold is released back; nothing stranded in escrow.
        assertThat(balance(a, "GOLD")).isEqualTo(200);
        assertThat(balance(-2L, "GOLD")).isZero(); // escrow owner
        assertThat(reconciliation.reconcile().ok()).isTrue();
    }

    @Test
    void crashBetweenEscrowStepsIsRecoveredToCompletion() {
        long a = 7001;
        long b = 7002;
        economy.mint(UUID.randomUUID(), a, "GOLD", 150);
        economy.mint(UUID.randomUUID(), b, "ITEM:gold_ring", 1);

        UUID ext = UUID.randomUUID();
        TradeRequest req = new TradeRequest(a, b,
                List.of(new AssetAmount("GOLD", 150)),
                List.of(new AssetAmount("ITEM:gold_ring", 1)));

        faults.armCrashAt("after-escrow-a");
        assertThatThrownBy(() -> orchestrator.startAndRun(ext, req))
                .isInstanceOf(SagaFaultInjector.InjectedCrash.class);

        // Value is escrowed, not stranded/duplicated: A debited, escrow holds it.
        assertThat(balance(a, "GOLD")).isZero();
        assertThat(balance(-2L, "GOLD")).isEqualTo(150);

        // Recovery drives the interrupted saga to completion.
        int recovered = recovery.recoverStuckSagas();
        assertThat(recovered).isGreaterThanOrEqualTo(1);

        assertThat(balance(b, "GOLD")).isEqualTo(150);
        assertThat(balance(a, "ITEM:gold_ring")).isEqualTo(1);
        assertThat(balance(-2L, "GOLD")).isZero();
        assertThat(reconciliation.reconcile().ok()).isTrue();
    }
}
