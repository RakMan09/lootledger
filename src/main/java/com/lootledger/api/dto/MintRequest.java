package com.lootledger.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record MintRequest(
        @NotNull Long toOwnerId,
        @NotBlank String asset,
        @Positive long amount) {
}
