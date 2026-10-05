/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import com.chunkworks.serfdom.domain.Verdict.Reaction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions:
 * <ul>
 * <li>a stall: open; no item, stock short of a sale, proceeds short of a price; quantity and price
 * at 1 and 64 and outside; its price an item; its sales in a row, limited by stock, by room;</li>
 * <li>the chance: under, at, between, at 150% of and over what it will pay; what it will pay of 0
 * refused;</li>
 * <li>the verdict: no value; a purse short of one sale, exact; a draw under the chance, at it, over
 * it; a bargain (at and under the base value) and a plain purchase; sales limited by what it wants,
 * the day's left, the stall's, the purse;</li>
 * <li>facts and outcomes that break their invariants refused.</li>
 * </ul> */
final class VerdictTest {
    private static Stall stall(int quantity, int price) { return new Stall("bread", quantity, price, 64, 576); }
    private static Verdict.Outcome judge(double base, Stall s, int emeralds, int wanted, int left, double draw) {
        return Verdict.judge(new Verdict.Facts(base, 1.5, s, emeralds, wanted, left, draw));
    }

    @Test void aStallIsOpenWithAnItemAStocksSaleAndRoomForItsPrice() {
        assertTrue(new Stall("bread", 4, 1, 4, 1).open());
        assertFalse(new Stall("", 4, 1, 64, 64).open(), "no item");
        assertFalse(new Stall("bread", 4, 1, 3, 64).open(), "stock short of a sale");
        assertFalse(new Stall("bread", 4, 2, 64, 1).open(), "proceeds full");
        assertEquals(0.25, new Stall("bread", 4, 1, 0, 0).each());
        assertEquals(3, new Stall("bread", 4, 2, 13, 64).sales(), "by stock");
        assertEquals(2, new Stall("bread", 4, 2, 64, 5).sales(), "by room");
        assertEquals(0, new Stall("", 4, 2, 64, 64).sales());
        assertDoesNotThrow(() -> new Stall("bread", 64, 64, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Stall("bread", 0, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Stall("bread", 65, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Stall("bread", 1, 65, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Stall("bread", 1, 1, -1, 0));
    }

    @Test void theChanceFallsInAStraightLineToNothingAtHalfAgainWhatItWillPay() {
        assertEquals(1.0, Verdict.chance(0.5, 1.0));
        assertEquals(1.0, Verdict.chance(1.0, 1.0));
        assertEquals(0.5, Verdict.chance(1.25, 1.0), 1e-12);
        assertEquals(0.0, Verdict.chance(1.5, 1.0), 1e-12);
        assertEquals(0.0, Verdict.chance(3.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> Verdict.chance(1, 0));
        assertEquals(0.375, Verdict.willing(0.25, 1.5));
    }

    @Test void aThingOfNoValueIsNotInteresting() {
        assertEquals(new Verdict.Outcome(Reaction.NOT_INTERESTED, 0), judge(0, stall(1, 1), 10, 1, 3, 0));
    }

    @Test void aPurseShortOfOneSaleCantAffordIt() {
        assertEquals(Reaction.CANT_AFFORD, judge(1, stall(4, 3), 2, 1, 3, 0).reaction());
        assertEquals(Reaction.BARGAIN, judge(1, stall(4, 3), 3, 1, 3, 0).reaction(), "exact");
    }

    @Test void overWhatItWillPayTheDrawDecides() {
        // Bread at 1/6, a need: it will pay 0.25 an item; sixteen for 5 is 0.3125 an item, chance 0.5.
        var dear = stall(4, 1);
        var s = new Stall("bread", 16, 5, 64, 576);
        assertEquals(0.5, Verdict.chance(s.each(), Verdict.willing(1 / 6.0, 1.5)), 1e-12);
        assertEquals(Reaction.BOUGHT, judge(1 / 6.0, s, 10, 1, 3, 0.49).reaction());
        assertEquals(Reaction.TOO_PRICEY, judge(1 / 6.0, s, 10, 1, 3, 0.5).reaction(), "a draw at the chance");
        assertEquals(Reaction.TOO_PRICEY, judge(1 / 6.0, s, 10, 1, 3, 0.9).reaction());
        assertEquals(Reaction.BOUGHT, judge(1 / 6.0, dear, 10, 1, 3, 0.999).reaction(), "at what it will pay it always buys");
        assertEquals(Reaction.TOO_PRICEY, judge(1 / 6.0, stall(1, 1), 10, 1, 3, 0).reaction(), "six times its worth: never");
    }

    @Test void atOrUnderItsBaseValueIsABargain() {
        assertEquals(Reaction.BARGAIN, judge(1 / 6.0, stall(6, 1), 10, 1, 3, 0.99).reaction(), "at it");
        assertEquals(Reaction.BARGAIN, judge(1 / 6.0, stall(8, 1), 10, 1, 3, 0.99).reaction(), "under it");
        assertEquals(Reaction.BOUGHT, judge(1 / 6.0, stall(5, 1), 10, 1, 3, 0.99).reaction(), "over it, within what it will pay");
    }

    @Test void itTakesTheSalesItWantsWithinTheDayTheStallAndThePurse() {
        assertEquals(2, judge(1, stall(1, 1), 10, 2, 3, 0).sales(), "what it wants");
        assertEquals(1, judge(1, stall(1, 1), 10, 5, 1, 0).sales(), "the day's last");
        assertEquals(2, judge(1, new Stall("bread", 1, 1, 2, 64), 10, 5, 3, 0).sales(), "the stall's stock");
        assertEquals(1, judge(1, new Stall("bread", 1, 1, 9, 1), 10, 5, 3, 0).sales(), "the stall's room");
        assertEquals(2, judge(1, stall(1, 2), 5, 5, 3, 0).sales(), "the purse: five pays two at 2");
    }

    @Test void factsAndOutcomesKeepTheirInvariants() {
        var open = stall(1, 1);
        assertThrows(IllegalArgumentException.class, () -> new Verdict.Facts(1, 1.5, new Stall("bread", 1, 1, 0, 9), 5, 1, 1, 0), "a closed stall");
        assertThrows(IllegalArgumentException.class, () -> new Verdict.Facts(1, 0, open, 5, 1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Verdict.Facts(1, 1.5, open, 5, 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Verdict.Facts(1, 1.5, open, 5, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Verdict.Facts(1, 1.5, open, 5, 1, 1, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new Verdict.Outcome(Reaction.BOUGHT, 0));
        assertThrows(IllegalArgumentException.class, () -> new Verdict.Outcome(Reaction.TOO_PRICEY, 1));
    }
}
