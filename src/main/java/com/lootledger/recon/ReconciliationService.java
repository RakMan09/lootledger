package com.lootledger.recon;

import com.lootledger.ledger.InvariantChecker;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Periodically recomputes balances from the immutable posting ledger and asserts the four core
 * invariants. Publishes a {@code lootledger.invariant.violations} gauge and logs/alerts on drift.
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final InvariantChecker checker;
    private final AtomicInteger lastViolationCount = new AtomicInteger(0);

    public ReconciliationService(InvariantChecker checker, MeterRegistry meterRegistry) {
        this.checker = checker;
        meterRegistry.gauge("lootledger.invariant.violations", lastViolationCount);
    }

    @Scheduled(fixedDelayString = "${lootledger.reconciliation.interval-ms:30000}")
    public InvariantChecker.Report reconcile() {
        InvariantChecker.Report report = checker.check();
        lastViolationCount.set(report.violations().size());
        if (report.ok()) {
            log.info("Reconciliation OK: all invariants hold");
        } else {
            log.error("Reconciliation DRIFT DETECTED: {} violation(s): {}",
                    report.violations().size(), report.violations());
        }
        return report;
    }
}
