package com.lootledger.repository;

import com.lootledger.domain.Transfer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<Transfer, Long> {
    Optional<Transfer> findByExternalId(UUID externalId);
}
