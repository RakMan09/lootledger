package com.lootledger.repository;

import com.lootledger.domain.Account;
import com.lootledger.domain.AccountKind;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByOwnerIdAndAsset(Long ownerId, String asset);

    List<Account> findByOwnerId(Long ownerId);

    List<Account> findByKind(AccountKind kind);

    /**
     * Pessimistic lock the given accounts. Callers must pass ids sorted ascending so that all
     * transactions acquire locks in the same deterministic order, avoiding deadlocks.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id in :ids order by a.id asc")
    List<Account> lockAllById(@Param("ids") List<Long> ids);

    @Query("select coalesce(sum(a.balance), 0) from Account a where a.asset = :asset and a.kind = :kind")
    long sumBalanceByAssetAndKind(@Param("asset") String asset, @Param("kind") AccountKind kind);

    @Query("select coalesce(sum(a.balance), 0) from Account a where a.asset = :asset")
    long sumBalanceByAsset(@Param("asset") String asset);
}
