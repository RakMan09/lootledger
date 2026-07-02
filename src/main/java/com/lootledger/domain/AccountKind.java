package com.lootledger.domain;

/** Category of an account. Only PLAYER accounts may not go negative. */
public enum AccountKind {
    PLAYER,
    FAUCET, // mints value into the economy
    SINK,   // burns value out of the economy
    ESCROW  // holds value mid-trade
}
