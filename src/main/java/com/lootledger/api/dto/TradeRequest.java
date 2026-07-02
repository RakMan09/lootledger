package com.lootledger.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * A two-sided swap: party A gives {@code aGives} and party B gives {@code bGives}. Each side's assets
 * are escrowed then crossed to the counterparty.
 */
public record TradeRequest(
        @NotNull Long partyAOwnerId,
        @NotNull Long partyBOwnerId,
        @NotEmpty @Valid List<AssetAmount> aGives,
        @NotEmpty @Valid List<AssetAmount> bGives) {
}
