package com.lootledger.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lootledger.domain.Account;
import com.lootledger.domain.AccountKind;
import com.lootledger.ledger.InvariantChecker;
import com.lootledger.repository.AccountRepository;
import com.lootledger.repository.PostingRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class InvariantCheckerTest {

    @Mock
    AccountRepository accounts;

    @Mock
    PostingRepository postings;

    InvariantChecker checker;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        checker = new InvariantChecker(accounts, postings);
    }

    private Account account(long id, String asset, AccountKind kind, long balance) {
        return Account.builder().id(id).ownerId(id).asset(asset).kind(kind).balance(balance).build();
    }

    private PostingRepository.AccountBalanceProjection proj(long accountId, long total) {
        return new PostingRepository.AccountBalanceProjection() {
            @Override
            public Long getAccountId() {
                return accountId;
            }

            @Override
            public Long getTotal() {
                return total;
            }
        };
    }

    @Test
    void balancedConservedLedgerHasNoViolations() {
        when(accounts.findAll()).thenReturn(List.of(
                account(1, "GOLD", AccountKind.FAUCET, -100),
                account(2, "GOLD", AccountKind.PLAYER, 100)));
        when(postings.sumGroupedByAccount()).thenReturn(List.of(proj(1, -100), proj(2, 100)));

        InvariantChecker.Report report = checker.check();

        assertThat(report.ok()).isTrue();
    }

    @Test
    void cacheDriftIsDetected() {
        when(accounts.findAll()).thenReturn(List.of(
                account(1, "GOLD", AccountKind.FAUCET, -100),
                account(2, "GOLD", AccountKind.PLAYER, 100)));
        // Ledger says account 2 should be 90, but cache says 100.
        when(postings.sumGroupedByAccount()).thenReturn(List.of(proj(1, -100), proj(2, 90)));

        InvariantChecker.Report report = checker.check();

        assertThat(report.ok()).isFalse();
        assertThat(report.violations()).anyMatch(v -> v.invariant().equals("CACHE_MATCHES_LEDGER"));
    }

    @Test
    void negativePlayerBalanceIsDetected() {
        when(accounts.findAll()).thenReturn(List.of(
                account(1, "GOLD", AccountKind.FAUCET, 50),
                account(2, "GOLD", AccountKind.PLAYER, -50)));
        when(postings.sumGroupedByAccount()).thenReturn(List.of(proj(1, 50), proj(2, -50)));

        InvariantChecker.Report report = checker.check();

        assertThat(report.violations()).anyMatch(v -> v.invariant().equals("NO_NEGATIVE_BALANCE"));
    }

    @Test
    void nonZeroConservationIsDetected() {
        when(accounts.findAll()).thenReturn(List.of(
                account(1, "GOLD", AccountKind.FAUCET, -100),
                account(2, "GOLD", AccountKind.PLAYER, 150)));
        when(postings.sumGroupedByAccount()).thenReturn(List.of(proj(1, -100), proj(2, 150)));

        InvariantChecker.Report report = checker.check();

        assertThat(report.violations()).anyMatch(v -> v.invariant().equals("CONSERVATION"));
    }
}
