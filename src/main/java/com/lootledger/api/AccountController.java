package com.lootledger.api;

import com.lootledger.api.dto.BalanceResponse;
import com.lootledger.domain.Account;
import com.lootledger.ledger.LedgerService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final LedgerService ledger;

    public AccountController(LedgerService ledger) {
        this.ledger = ledger;
    }

    @GetMapping("/{ownerId}/balances")
    public BalanceResponse balances(@PathVariable long ownerId) {
        List<BalanceResponse.Entry> entries = ledger.balancesFor(ownerId).stream()
                .map(this::toEntry)
                .toList();
        return new BalanceResponse(ownerId, entries);
    }

    private BalanceResponse.Entry toEntry(Account a) {
        return new BalanceResponse.Entry(a.getAsset(), a.getKind().name(), a.getBalance());
    }
}
