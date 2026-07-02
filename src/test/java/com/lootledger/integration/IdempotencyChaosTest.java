package com.lootledger.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.lootledger.api.ExternalIds;
import com.lootledger.api.dto.TransferRequest;
import com.lootledger.domain.Account;
import com.lootledger.economy.EconomyService;
import com.lootledger.recon.ReconciliationService;
import com.lootledger.repository.AccountRepository;
import com.lootledger.repository.TransferRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * The headline "try to dupe my own economy" test: fire the same transfer with the same
 * Idempotency-Key from many threads at once and assert exactly one transfer was created and totals
 * are unchanged.
 */
class IdempotencyChaosTest extends AbstractIntegrationTest {

    private static final int THREADS = 200;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    EconomyService economy;

    @Autowired
    AccountRepository accounts;

    @Autowired
    TransferRepository transfers;

    @Autowired
    ReconciliationService reconciliation;

    @Test
    void concurrentDuplicateStormCreatesExactlyOneTransfer() throws Exception {
        long sender = 90001;
        long receiver = 90002;
        economy.mint(UUID.randomUUID(), sender, "GOLD", 1_000_000);

        String key = "chaos-" + UUID.randomUUID();
        TransferRequest body = new TransferRequest(sender, receiver, "GOLD", 100);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", key);
        HttpEntity<TransferRequest> request = new HttpEntity<>(body, headers);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger success2xx = new AtomicInteger();
        AtomicInteger nonReplayed = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    ResponseEntity<String> resp = rest.postForEntity("/transfers", request, String.class);
                    if (resp.getStatusCode().is2xxSuccessful()) {
                        success2xx.incrementAndGet();
                        if ("false".equals(resp.getHeaders().getFirst("Idempotent-Replayed"))) {
                            nonReplayed.incrementAndGet();
                        }
                    } else {
                        other.incrementAndGet();
                    }
                } catch (Exception e) {
                    other.incrementAndGet();
                }
            });
        }

        ready.await(30, TimeUnit.SECONDS);
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(120, TimeUnit.SECONDS)).isTrue();

        // Exactly one real execution; every caller saw success.
        assertThat(nonReplayed.get()).isEqualTo(1);
        assertThat(success2xx.get()).isEqualTo(THREADS);
        assertThat(other.get()).isZero();

        // Exactly one transfer row exists for the derived external id.
        assertThat(transfers.findByExternalId(ExternalIds.fromKey(key))).isPresent();

        // The receiver was credited exactly once.
        Account receiverAcct = accounts.findByOwnerIdAndAsset(receiver, "GOLD").orElseThrow();
        assertThat(receiverAcct.getBalance()).isEqualTo(100);

        // And value is conserved across the whole economy.
        assertThat(reconciliation.reconcile().ok()).isTrue();
    }

    private long goldOf(long owner) {
        List<Account> list = accounts.findByOwnerId(owner);
        return list.stream().filter(a -> a.getAsset().equals("GOLD")).mapToLong(Account::getBalance).findFirst().orElse(0);
    }
}
