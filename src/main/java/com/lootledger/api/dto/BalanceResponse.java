package com.lootledger.api.dto;

import java.util.List;

public record BalanceResponse(long ownerId, List<Entry> balances) {

    public record Entry(String asset, String kind, long balance) {
    }
}
