package com.lootledger.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Central place for the quantifiable business metrics the project advertises: throughput and latency
 * of transfers, how many duplicate requests were deduplicated (dupes prevented), and trade outcomes.
 * All meters are also exported on {@code /actuator/prometheus}.
 */
@Component
public class EconomyMetrics {

    private final Counter transfers;
    private final Counter mints;
    private final Counter tradesCompleted;
    private final Counter tradesCompensated;
    private final Counter idempotentExecutions;
    private final Counter idempotentReplays;
    private final Counter overdraftsRejected;
    private final Timer transferLatency;

    public EconomyMetrics(MeterRegistry registry) {
        this.transfers = Counter.builder("lootledger.transfers.total")
                .description("Transfers successfully posted").register(registry);
        this.mints = Counter.builder("lootledger.mints.total")
                .description("Faucet mints (incl. loot) posted").register(registry);
        this.tradesCompleted = Counter.builder("lootledger.trades.completed")
                .description("Two-sided trades that completed").register(registry);
        this.tradesCompensated = Counter.builder("lootledger.trades.compensated")
                .description("Trades rolled back via compensation").register(registry);
        this.idempotentExecutions = Counter.builder("lootledger.idempotency.executed")
                .description("First-time (fresh) executions of an idempotency key").register(registry);
        this.idempotentReplays = Counter.builder("lootledger.idempotency.replayed")
                .description("Duplicate requests deduplicated (dupes prevented)").register(registry);
        this.overdraftsRejected = Counter.builder("lootledger.overdrafts.rejected")
                .description("Transfers rejected for insufficient funds").register(registry);
        this.transferLatency = Timer.builder("lootledger.transfer.latency")
                .description("Ledger transfer processing latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);
    }

    public void recordTransfer() {
        transfers.increment();
    }

    public void recordMint() {
        mints.increment();
    }

    public void recordTradeCompleted() {
        tradesCompleted.increment();
    }

    public void recordTradeCompensated() {
        tradesCompensated.increment();
    }

    public void recordIdempotentExecution() {
        idempotentExecutions.increment();
    }

    public void recordIdempotentReplay() {
        idempotentReplays.increment();
    }

    public void recordOverdraftRejected() {
        overdraftsRejected.increment();
    }

    public Timer transferLatencyTimer() {
        return transferLatency;
    }

    /** A human-friendly snapshot for the dashboard's live metric tiles. */
    public Map<String, Object> summary() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("transfers", (long) transfers.count());
        out.put("mints", (long) mints.count());
        out.put("tradesCompleted", (long) tradesCompleted.count());
        out.put("tradesCompensated", (long) tradesCompensated.count());
        out.put("idempotentExecutions", (long) idempotentExecutions.count());
        out.put("duplicatesPrevented", (long) idempotentReplays.count());
        out.put("overdraftsRejected", (long) overdraftsRejected.count());

        HistogramSnapshot snap = transferLatency.takeSnapshot();
        Map<String, Object> latency = new LinkedHashMap<>();
        latency.put("count", snap.count());
        latency.put("meanMs", round(snap.mean(TimeUnit.MILLISECONDS)));
        latency.put("maxMs", round(snap.max(TimeUnit.MILLISECONDS)));
        for (ValueAtPercentile p : snap.percentileValues()) {
            latency.put("p" + (int) (p.percentile() * 100) + "Ms", round(p.value(TimeUnit.MILLISECONDS)));
        }
        out.put("transferLatency", latency);
        return out;
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
