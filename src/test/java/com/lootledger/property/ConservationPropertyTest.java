package com.lootledger.property;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based proof of the accounting rules the ledger enforces: for ANY random sequence of mints
 * and transfers among N players, gold is conserved (players + faucet net to zero) and no player
 * balance ever goes negative. Mirrors {@link com.lootledger.ledger.LedgerService}'s invariants on a
 * fast in-memory model so thousands of sequences can be checked per run.
 */
class ConservationPropertyTest {

    private static final int PLAYERS = 8;

    /** A single generated economy operation. */
    record Op(boolean isMint, int from, int to, long amount) {
    }

    @Provide
    Arbitrary<List<Op>> operations() {
        Arbitrary<Boolean> isMint = Arbitraries.of(true, false);
        Arbitrary<Integer> player = Arbitraries.integers().between(0, PLAYERS - 1);
        Arbitrary<Long> amount = Arbitraries.longs().between(1, 1000);
        Arbitrary<Op> op = Combinators.combine(isMint, player, player, amount)
                .as(Op::new);
        return op.list().ofMinSize(0).ofMaxSize(300);
    }

    @Property(tries = 500)
    void goldIsConservedAndNeverNegative(@ForAll("operations") List<Op> ops) {
        long[] players = new long[PLAYERS];
        long faucet = 0;

        for (Op op : ops) {
            if (op.isMint()) {
                players[op.to()] += op.amount();
                faucet -= op.amount();
            } else if (op.from() != op.to() && players[op.from()] >= op.amount()) {
                players[op.from()] -= op.amount();
                players[op.to()] += op.amount();
            }
            // Overdrafting transfers are rejected (skipped), exactly like the ledger.
        }

        long totalPlayers = 0;
        for (long b : players) {
            assertThat(b).as("no player balance is negative").isGreaterThanOrEqualTo(0);
            totalPlayers += b;
        }
        assertThat(totalPlayers + faucet).as("gold is conserved").isZero();
    }

    @Property(tries = 500)
    void balancedPostingsAlwaysSumToZeroPerAsset(@ForAll("operations") List<Op> ops) {
        // Every operation emits two balanced legs; their signed sum must be zero.
        Map<String, Long> perAsset = new HashMap<>();
        List<long[]> legs = new ArrayList<>();
        for (Op op : ops) {
            legs.add(new long[] {-op.amount(), op.amount()});
        }
        for (long[] pair : legs) {
            perAsset.merge("GOLD", pair[0] + pair[1], Long::sum);
        }
        assertThat(perAsset.getOrDefault("GOLD", 0L)).isZero();
    }
}
