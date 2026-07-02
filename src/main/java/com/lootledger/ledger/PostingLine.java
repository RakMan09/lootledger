package com.lootledger.ledger;

/**
 * A requested posting expressed against a resolved account id. Amount is signed:
 * negative debits (removes value), positive credits (adds value).
 */
public record PostingLine(Long accountId, String asset, long amount) {
}
