package com.lootledger.economy;

/** Well-known owner ids for non-player system accounts. */
public final class SystemAccounts {

    /** Owner id for faucet (mint) accounts, one per asset. */
    public static final long FAUCET_OWNER = 0L;

    /** Owner id for sink (burn) accounts, one per asset. */
    public static final long SINK_OWNER = -1L;

    /** Owner id for the shared escrow accounts, one per asset. */
    public static final long ESCROW_OWNER = -2L;

    public static final String GOLD = "GOLD";

    private SystemAccounts() {
    }

    public static String itemAsset(String itemType) {
        return "ITEM:" + itemType;
    }
}
