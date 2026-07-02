package com.lootledger.repository;

import com.lootledger.domain.Posting;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostingRepository extends JpaRepository<Posting, Long> {

    List<Posting> findByTransferId(Long transferId);

    @Query("select coalesce(sum(p.amount), 0) from Posting p where p.accountId = :accountId")
    long sumByAccountId(@Param("accountId") Long accountId);

    @Query("select p.accountId as accountId, coalesce(sum(p.amount),0) as total "
            + "from Posting p group by p.accountId")
    List<AccountBalanceProjection> sumGroupedByAccount();

    interface AccountBalanceProjection {
        Long getAccountId();

        Long getTotal();
    }
}
