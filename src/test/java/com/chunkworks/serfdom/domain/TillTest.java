/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions:
 * <ul>
 * <li>the offer: the villager buys (its result is emeralds); it sells (a cost is emeralds, in the
 * first place, the second, both); emeralds both ways; none (items for items); negatives refused;</li>
 * <li>open: a purse short of the payment, exact, over; a payment the takings cover;</li>
 * <li>after: paying; taking in under the cap and past it; netted both ways; a closed trade
 * refused.</li>
 * </ul> */
final class TillTest {
    private static final String E = Till.EMERALD;
    private static final Purse.Rules R = Purse.Rules.DEFAULT;
    private static Purse of(int n) { return new Purse(n, Purse.NEVER); }

    @Test void anOfferIsReadForWhatItPaysAndWhatItTakes() {
        assertEquals(new Till(1, 0), Till.of("minecraft:wheat", 20, "", 0, E, 1), "the villager buys wheat");
        assertEquals(new Till(0, 3), Till.of(E, 3, "", 0, "minecraft:bread", 6), "it sells bread");
        assertEquals(new Till(0, 5), Till.of("minecraft:book", 1, E, 5, "minecraft:enchanted_book", 1), "emeralds as the second cost");
        assertEquals(new Till(0, 9), Till.of(E, 4, E, 5, "minecraft:diamond", 1), "both costs");
        assertEquals(new Till(2, 1), Till.of(E, 1, "", 0, E, 2), "both ways");
        assertEquals(Till.NONE, Till.of("minecraft:gravel", 10, "", 0, "minecraft:flint", 10), "no emeralds at all");
        assertTrue(Till.NONE.none());
        assertFalse(new Till(1, 0).none());
        assertThrows(IllegalArgumentException.class, () -> new Till(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Till(0, -1));
    }

    @Test void aTradeIsOpenWhileThePurseCoversItsPaymentLessItsTakings() {
        var buy = new Till(2, 0);
        assertFalse(buy.open(of(1)), "short");
        assertTrue(buy.open(of(2)), "exact");
        assertTrue(buy.open(of(9)));
        assertTrue(new Till(0, 5).open(of(0)), "a sale is always open");
        assertTrue(new Till(3, 2).open(of(1)), "the takings cover the rest");
        assertFalse(new Till(3, 2).open(of(0)));
    }

    @Test void afterATradeThePurseHasPaidOrTakenNetAndThePastTheCapIsLost() {
        assertEquals(1, new Till(2, 0).after(of(3), R).emeralds());
        assertEquals(8, new Till(0, 5).after(of(3), R).emeralds());
        assertEquals(64, new Till(0, 5).after(of(62), R).emeralds());
        assertEquals(2, new Till(3, 2).after(of(3), R).emeralds(), "netted: paid one");
        assertEquals(64, new Till(1, 4).after(of(62), R).emeralds(), "netted: took three, one lost");
        assertThrows(IllegalArgumentException.class, () -> new Till(2, 0).after(of(1), R));
    }
}
