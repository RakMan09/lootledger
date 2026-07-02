package com.lootledger.recon;

import com.lootledger.ledger.InvariantChecker;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/reconciliation")
public class ReconciliationController {

    private final ReconciliationService reconciliation;

    public ReconciliationController(ReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    @GetMapping
    public InvariantChecker.Report run() {
        return reconciliation.reconcile();
    }
}
