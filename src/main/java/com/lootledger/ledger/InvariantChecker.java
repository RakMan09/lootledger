package com.lootledger.ledger;

import com.lootledger.domain.Account;
import com.lootledger.domain.AccountKind;
import com.lootledger.repository.AccountRepository;
import com.lootledger.repository.PostingRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the four core invariants by recomputing balances from the immutable posting ledger and
 * comparing them to the cached {@code account.balance} column.
 *
 * <ol>
 *   <li>Each transfer's postings sum to zero per asset (enforced at write time).</li>
 *   <li>{@code account.balance} equals the sum of its postings (cache matches ledger).</li>
 *   <li>No PLAYER/ESCROW/SINK account balance is negative.</li>
 *   <li>Total value per asset across all accounts is zero (conservation; faucets carry the negative
 *       of what they've minted).</li>
 * </ol>
 */
@Service
public class InvariantChecker {

    private final AccountRepository accounts;
    private final PostingRepository postings;

    public InvariantChecker(AccountRepository accounts, PostingRepository postings) {
        this.accounts = accounts;
        this.postings = postings;
    }

    public record Violation(String invariant, String detail) {
    }

    public record Report(boolean ok, List<Violation> violations) {
    }

    @Transactional(readOnly = true)
    public Report check() {
        List<Violation> violations = new ArrayList<>();

        Map<Long, Long> ledgerByAccount = postings.sumGroupedByAccount().stream()
                .collect(Collectors.toMap(
                        PostingRepository.AccountBalanceProjection::getAccountId,
                        PostingRepository.AccountBalanceProjection::getTotal));

        List<Account> all = accounts.findAll();
        Map<String, Long> totalPerAsset = new HashMap<>();

        for (Account account : all) {
            long ledger = ledgerByAccount.getOrDefault(account.getId(), 0L);

            // Invariant 2: cache matches ledger.
            if (account.getBalance() != ledger) {
                violations.add(new Violation(
                        "CACHE_MATCHES_LEDGER",
                        "account " + account.getId() + " cache=" + account.getBalance() + " ledger=" + ledger));
            }

            // Invariant 3: only faucets may be negative.
            if (account.getBalance() < 0 && account.getKind() != AccountKind.FAUCET) {
                violations.add(new Violation(
                        "NO_NEGATIVE_BALANCE",
                        "account " + account.getId() + " kind=" + account.getKind()
                                + " balance=" + account.getBalance()));
            }

            totalPerAsset.merge(account.getAsset(), account.getBalance(), Long::sum);
        }

        // Invariant 4: conservation — every asset nets to zero across all accounts.
        for (Map.Entry<String, Long> e : totalPerAsset.entrySet()) {
            if (e.getValue() != 0L) {
                violations.add(new Violation(
                        "CONSERVATION",
                        "asset " + e.getKey() + " total across accounts=" + e.getValue() + ", expected 0"));
            }
        }

        return new Report(violations.isEmpty(), violations);
    }
}
