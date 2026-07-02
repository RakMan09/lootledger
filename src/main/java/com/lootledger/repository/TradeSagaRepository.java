package com.lootledger.repository;

import com.lootledger.domain.TradeSaga;
import com.lootledger.domain.TradeSagaState;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeSagaRepository extends JpaRepository<TradeSaga, Long> {

    Optional<TradeSaga> findByExternalId(UUID externalId);

    List<TradeSaga> findByStateIn(List<TradeSagaState> states);
}
