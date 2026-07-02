package com.lootledger.api.dto;

public record TransferResponse(long transferId, String externalId, String type, String status) {
}
