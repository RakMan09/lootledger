package com.lootledger.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record AssetAmount(
        @NotBlank String asset,
        @Positive long amount) {
}
