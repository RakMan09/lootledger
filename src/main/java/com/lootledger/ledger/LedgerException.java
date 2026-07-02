package com.lootledger.ledger;

/** Thrown when a ledger operation violates a business invariant (e.g. overdraft, unbalanced postings). */
public class LedgerException extends RuntimeException {

    private final String code;

    public LedgerException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
