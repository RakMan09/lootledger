package com.lootledger.metrics;

import com.lootledger.domain.AccountKind;
import com.lootledger.repository.AccountRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Human-friendly quantifiable metrics for the dashboard. The same data (and more) is available in
 * Prometheus format at {@code /actuator/prometheus}.
 */
@RestController
@RequestMapping("/admin/metrics")
public class MetricsController {

    private final EconomyMetrics metrics;
    private final AccountRepository accounts;

    public MetricsController(EconomyMetrics metrics, AccountRepository accounts) {
        this.metrics = metrics;
        this.accounts = accounts;
    }

    @GetMapping
    public Map<String, Object> summary() {
        Map<String, Object> out = new LinkedHashMap<>(metrics.summary());
        long goldMinted = -accounts.sumBalanceByAssetAndKind("GOLD", AccountKind.FAUCET);
        long goldHeldByPlayers = accounts.sumBalanceByAssetAndKind("GOLD", AccountKind.PLAYER);
        out.put("goldMinted", goldMinted);
        out.put("goldHeldByPlayers", goldHeldByPlayers);
        out.put("accounts", accounts.count());
        return out;
    }
}
