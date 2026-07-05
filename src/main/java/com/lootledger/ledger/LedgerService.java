package com.lootledger.ledger;

import com.lootledger.domain.Account;
import com.lootledger.domain.AccountKind;
import com.lootledger.domain.Posting;
import com.lootledger.domain.Transfer;
import com.lootledger.repository.AccountRepository;
import com.lootledger.repository.PostingRepository;
import com.lootledger.repository.TransferRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one and only writer of account balances. Every mutation is a balanced {@link Transfer} of
 * {@link Posting} legs. Postings are validated to sum to zero per asset, accounts are locked in a
 * deterministic order to avoid deadlocks, and non-faucet accounts are prevented from going negative.
 */
@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final PostingRepository postings;
    private final JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager entityManager;

    public LedgerService(
            AccountRepository accounts,
            TransferRepository transfers,
            PostingRepository postings,
            JdbcTemplate jdbc) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.postings = postings;
        this.jdbc = jdbc;
    }

    /**
     * Get an account by (owner, asset), creating it if absent. Uses an idempotent
     * {@code INSERT ... ON CONFLICT DO NOTHING} so concurrent first-time creation of the same account
     * neither races nor throws (a caught constraint violation would poison the surrounding
     * transaction); concurrent inserts simply serialize on the unique index.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Account getOrCreateAccount(long ownerId, String asset, AccountKind kind) {
        Optional<Account> existing = accounts.findByOwnerIdAndAsset(ownerId, asset);
        if (existing.isPresent()) {
            return existing.get();
        }
        jdbc.update(
                "INSERT INTO account (owner_id, asset, kind, balance, version) VALUES (?, ?, ?, 0, 0) "
                        + "ON CONFLICT (owner_id, asset) DO NOTHING",
                ownerId, asset, kind.name());
        return accounts.findByOwnerIdAndAsset(ownerId, asset)
                .orElseThrow(() -> new LedgerException(
                        "ACCOUNT_CREATE_FAILED", "Could not create account for owner " + ownerId + " asset " + asset));
    }

    /**
     * Apply a balanced transfer. If a transfer with {@code externalId} already exists, this is a
     * no-op returning the existing transfer (ledger-level idempotency backstop).
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Transfer post(UUID externalId, String type, List<PostingLine> lines) {
        if (lines == null || lines.size() < 2) {
            throw new LedgerException("UNBALANCED", "A transfer needs at least two postings");
        }

        Optional<Transfer> existing = transfers.findByExternalId(externalId);
        if (existing.isPresent()) {
            log.debug("Transfer {} already exists, returning idempotently", externalId);
            return existing.get();
        }

        validateBalanced(lines);

        // Drop any accounts already loaded (e.g. by getOrCreateAccount) so the locking read below
        // returns authoritative, freshly-committed state — otherwise balance math could start from a
        // stale cached copy and lose updates / trigger optimistic-lock failures.
        entityManager.clear();

        // Lock all involved accounts in ascending id order.
        List<Long> ids = lines.stream()
                .map(PostingLine::accountId)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        List<Account> locked = accounts.lockAllById(ids);
        Map<Long, Account> byId = locked.stream().collect(Collectors.toMap(Account::getId, a -> a));
        if (byId.size() != ids.size()) {
            throw new LedgerException("ACCOUNT_NOT_FOUND", "One or more accounts do not exist");
        }

        Transfer transfer;
        try {
            transfer = transfers.saveAndFlush(Transfer.builder()
                    .externalId(externalId)
                    .type(type)
                    .createdAt(OffsetDateTime.now())
                    .build());
        } catch (DataIntegrityViolationException dup) {
            // Concurrent creation with same external id: return the winner.
            return transfers.findByExternalId(externalId).orElseThrow(() -> dup);
        }

        for (PostingLine line : lines) {
            Account account = byId.get(line.accountId());
            long newBalance = account.getBalance() + line.amount();
            if (newBalance < 0 && account.getKind() != AccountKind.FAUCET) {
                throw new LedgerException(
                        "INSUFFICIENT_FUNDS",
                        "Account " + account.getId() + " (" + account.getAsset()
                                + ") would go negative: " + newBalance);
            }
            account.setBalance(newBalance);
            postings.save(Posting.builder()
                    .transferId(transfer.getId())
                    .accountId(account.getId())
                    .asset(line.asset())
                    .amount(line.amount())
                    .build());
        }
        accounts.saveAll(locked);
        log.info("Posted transfer {} type={} legs={}", externalId, type, lines.size());
        return transfer;
    }

    private void validateBalanced(List<PostingLine> lines) {
        Map<String, Long> perAsset = new HashMap<>();
        for (PostingLine line : lines) {
            perAsset.merge(line.asset(), line.amount(), Long::sum);
        }
        for (Map.Entry<String, Long> e : perAsset.entrySet()) {
            if (e.getValue() != 0L) {
                throw new LedgerException(
                        "UNBALANCED",
                        "Postings for asset " + e.getKey() + " sum to " + e.getValue() + ", expected 0");
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Account> balancesFor(long ownerId) {
        List<Account> list = new ArrayList<>(accounts.findByOwnerId(ownerId));
        list.sort(Comparator.comparing(Account::getAsset));
        return list;
    }

    @Transactional(readOnly = true)
    public Optional<Account> findAccount(long ownerId, String asset) {
        return accounts.findByOwnerIdAndAsset(ownerId, asset);
    }
}
